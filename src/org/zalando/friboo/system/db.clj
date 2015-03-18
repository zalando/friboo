(ns org.zalando.friboo.system.db
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as r]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log])
  (:import (com.jolbox.bonecp BoneCPDataSource)))

(defn start-component [component]
  (if (:datasource component)
    (do
      (log/debug "skipping start of DB connection pool; already running")
      component)

    (do
      (let [configuration (:configuration component)
            jdbc-url (str "jdbc:" (:subprotocol configuration) ":" (:subname configuration))]
        (log/info "starting DB connection pool for" jdbc-url)
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
      (log/debug "skipping stop of DB connection pool; not running")
      component)

    (do
      (log/info "stopping DB connection pool")
      (.close (:datasource component))
      (assoc component :pool nil))))

(defmacro def-db-component
  "Defines a new database component."
  [name]
  ; 'configuration' must be provided during initialization
  ; 'datasource' is the internal state
  ; HINT: this component is itself a valid db-spec as its a map with the key 'datasource'
  `(defrecord ~name [~(symbol "configuration") ~(symbol "datasource")]
     component/Lifecycle

     (start [this#]
       (start-component this#))

     (stop [this#]
       (stop-component this#))))
