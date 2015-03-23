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
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as r]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log])
  (:import (com.jolbox.bonecp BoneCPDataSource)
           (org.flywaydb.core Flyway)))

(defn start-component [component auto-migration?]
  (if (:datasource component)
    (do
      (log/debug "Skipping start of DB connection pool; already running.")
      component)

    (do
      (let [configuration (:configuration component)
            jdbc-url (str "jdbc:" (:subprotocol configuration) ":" (:subname configuration))]

        (when auto-migration?
          (log/info "Initiating automatic DB migration for %s." jdbc-url)
          (doto (Flyway.)
            (.setDataSource jdbc-url (:user configuration) (:password configuration) (make-array String 0))
            (.migrate)))

        (log/info "Starting DB connection pool for %s." jdbc-url)
        (let [partitions (or (:partitions configuration) 3)
              min-pool (or (:min-pool configuration) 5)
              max-pool (or (:max-pool configuration) 50)
              datasource (doto (BoneCPDataSource.)
                           (.setJdbcUrl jdbc-url)
                           (.setUsername (:user configuration))
                           (.setPassword (:password configuration))
                           (.setMinConnectionsPerPartition (inc (int (/ min-pool partitions))))
                           (.setMaxConnectionsPerPartition (inc (int (/ max-pool partitions))))
                           (.setPartitionCount partitions)
                           (.setStatisticsEnabled true)
                           (.setIdleConnectionTestPeriodInMinutes 25)
                           (.setIdleMaxAgeInMinutes (* 3 60))
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
           :or {auto-migration? false}}]
  ; 'configuration' must be provided during initialization
  ; 'datasource' is the internal state
  ; HINT: this component is itself a valid db-spec as its a map with the key 'datasource'
  `(defrecord ~name [~(symbol "configuration") ~(symbol "datasource")]
     component/Lifecycle

     (start [this#]
       (start-component this# ~auto-migration?))

     (stop [this#]
       (stop-component this#))))
