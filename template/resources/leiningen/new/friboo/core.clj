(ns {{namespace}}.core
  (:require [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.system.http :as http]
            [org.zalando.stups.friboo.log :as log]
            [com.stuartsierra.component :as component]
            [{{namespace}}.api :as api])
  (:gen-class))

(def default-http-config
  {:http-port 8080})

(defn run
  "Initializes and starts the whole system."
  [args-config]
  (let [config (config/load-config
                 (merge default-http-config
                        args-config)
                 [:http :api])
        system (component/map->SystemMap
                 {:http (component/using
                          (http/make-http "api.yaml" (:http config))
                          {:controller :api})
                  :api  (component/using
                          (api/map->Controller {:configuration (:api config)})
                          [])})]
    (system/run config system)))

(defn -main
  "The actual main for our uberjar."
  [& args]
  (try
    (run {})
    (catch Exception e
      (log/error e "Could not start the system because of %s." (str e))
      (System/exit 1))))
