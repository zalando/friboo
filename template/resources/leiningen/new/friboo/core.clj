(ns {{namespace}}.core
  (:require [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.log :as log]
            [{{namespace}}.db :as sql]
            [{{namespace}}.api :as api])
  (:gen-class))

(defn run
  "Initializes and starts the whole system."
  [default-configuration]
  (let [configuration (config/load-configuration
                        (system/default-http-namespaces-and :db)
                        [sql/default-db-configuration
                         api/default-http-configuration
                         default-configuration])
        system        (system/http-system-map
                        configuration
                        api/map->API [:db]
                        :db (sql/map->DB {:configuration (:db configuration)})
                        )]

    (system/run configuration system)))

(defn -main
  "The actual main for our uberjar."
  [& args]
  (try
    (run {})
    (catch Exception e
      (log/error e "Could not start system because of %s." (str e))
      (System/exit 1))))
