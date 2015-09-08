; Copyright © 2015 Zalando SE
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.zalando.stups.friboo.system.db
  (:require [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [clojure.data.json :as json]
            [com.netflix.hystrix.core :refer [defcommand]])
  (:import (com.jolbox.bonecp BoneCPDataSource)
           (org.flywaydb.core Flyway)
           (java.sql Timestamp)
           (java.io PrintWriter)
           (org.postgresql.util PSQLException)
           (com.netflix.hystrix.exception HystrixBadRequestException)
           (com.fasterxml.jackson.databind.util ISO8601Utils)))

(defn start-component [component auto-migration?]
  (if (:datasource component)
    (do
      (log/debug "Skipping start of DB connection pool; already running.")
      component)

    (do
      (let [configuration (:configuration component)
            jdbc-url (str "jdbc:" (require-config configuration :subprotocol) ":" (require-config configuration :subname))]

        (when auto-migration?
          (log/info "Initiating automatic DB migration for %s." jdbc-url)
          (doto (Flyway.)
            (.setDataSource jdbc-url (require-config configuration :user) (require-config configuration :password) (make-array String 0))
            (.migrate)))

        (log/info "Starting DB connection pool for %s." jdbc-url)
        (let [partitions (or (:partitions configuration) 3)
              min-pool (or (:min-pool configuration) 6)
              max-pool (or (:max-pool configuration) 21)
              datasource (doto (BoneCPDataSource.)
                           (.setJdbcUrl jdbc-url)
                           (.setUsername (require-config configuration :user))
                           (.setPassword (require-config configuration :password))
                           (.setMinConnectionsPerPartition (int (/ min-pool partitions)))
                           (.setMaxConnectionsPerPartition (int (/ max-pool partitions)))
                           (.setPartitionCount partitions)
                           (.setStatisticsEnabled true)
                           (.setIdleConnectionTestPeriodInMinutes 2)
                           (.setIdleMaxAgeInMinutes 10)
                           (.setInitSQL (or (:init-sql configuration) ""))
                           (.setConnectionTestStatement "SELECT 1"))]
          (assoc component :datasource datasource))))))

(defn stop-component [component]
  (if-not (:datasource component)
    (do
      (log/debug "Skipping stop of DB connection pool; not running.")
      component)

    (do
      (log/info "Stopping DB connection pool.")
      (.close (:datasource component))
      (assoc component :pool nil))))

(defmacro def-db-component
  "Defines a new database component."
  [name & {:keys [auto-migration?]
           :or   {auto-migration? false}}]
  ; 'configuration' must be provided during initialization
  ; 'datasource' is the internal state
  ; HINT: this component is itself a valid db-spec as its a map with the key 'datasource'
  `(defrecord ~name [~(symbol "configuration") ~(symbol "datasource")]
     component/Lifecycle

     (start [this#]
       (start-component this# ~auto-migration?))

     (stop [this#]
       (stop-component this#))))

; provide json serialization
(defn- serialize-sql-timestamp
  "Serializes a sql timestamp to json."
  [^Timestamp timestamp #^PrintWriter out]
  (.print out (json/write-str (ISO8601Utils/format timestamp true))))

; add json capability to java.sql.Timestamp
(extend Timestamp json/JSONWriter
  {:-write serialize-sql-timestamp})

; helper for hystrix wrapping

(defn ignore-nonfatal-psqlexception
  "Do not close curcuits because PSQLException with non-fatal error was thrown."
  [t]
  (when (instance? PSQLException t)
    (when-not (.startsWith (.getMessage t) "FATAL:")
      "non-fatal postgresql message")))

(defmacro generate-hystrix-commands
  "Wraps all functions in the used namespace"
  [& {:keys [prefix suffix ignore-exception-fn? namespace]
      :or {prefix "cmd-"
           suffix ""
           ignore-exception-fn? ignore-nonfatal-psqlexception
           namespace *ns*}}]
  `(do ~@(map (fn [[n f]]
                `(defcommand ~(symbol (str prefix (name n) suffix))
                   [& args#]
                   (try
                     (apply ~f args#)
                     (catch Throwable t#
                       (if-let [msg# (~ignore-exception-fn? t#)]
                         (throw (HystrixBadRequestException. msg# t#))
                         (throw t#))))))
              (ns-publics namespace))))
