(ns org.zalando.stups.friboo.system
  (:require [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log])
  (:import (clojure.lang ExceptionInfo)))

(defn run
  "Boots a whole new system."
  [{system-config :system} system]
  (log/info "Starting system...")
  (try
    (let [system (component/start system)]
      (log/info "System started.")
      system)
    (catch ExceptionInfo e
      (when-let [{:as exd :keys [system]} (ex-data e)]
        (component/stop system))
      (throw e))))
