(ns {{namespace}}.core
  (:require [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.system.http :as http]
            [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as core-appenders]
            [{{namespace}}.api :as api])
  (:gen-class))

(def default-http-config
  {:http-port 8080})

(defn default-log-output-fn
  ([data] (default-log-output-fn nil data))
  ([_ data] (let [{:keys [level ?ns-str ?msg-fmt vargs ?err]} data]
              (format "%5s [%s] %s - %s%s"
                      (str/upper-case (name level))
                      (.getName (Thread/currentThread))
                      (str ?ns-str)
                      (if-let [fmt ?msg-fmt]
                        (apply format fmt vargs)
                        (apply str vargs))
                      (str (when ?err (log/stacktrace ?err)))))))

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
  (log/set-config! {:level :debug
                    :appenders {:println (core-appenders/println-appender)}
                    :output-fn default-log-output-fn})
  (try
    (run {})
    (catch Exception e
      (log/error e "Could not start the system because of %s." (str e))
      (System/exit 1))))
