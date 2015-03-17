(ns org.zalando.friboo.system.db
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as r]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log])
  (:import (com.jolbox.bonecp BoneCPDataSource)))

(defrecord DB [configuration datasource]
  component/Lifecycle

  (start [this]
    (if datasource
      (do
        (log/debug "skipping start of DB connection pool; already running")
        this)

      (do
        (let [jdbc-url (str "jdbc:" (:subprotocol configuration) ":" (:subname configuration))]
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
            (assoc this :datasource datasource))))))

  (stop [this]
    (if-not datasource
      (do
        (log/debug "skipping stop of DB connection pool; not running")
        this)

      (do
        (log/info "stopping DB connection pool")
        (.close datasource)
        (assoc this :pool nil)))))

(defn new-db
  "Official constructor for the DB."
  [configuration]
  (map->DB {:configuration configuration}))
