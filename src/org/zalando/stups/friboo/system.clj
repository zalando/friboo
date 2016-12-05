(ns org.zalando.stups.friboo.system
  (:require [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log])
  (:import (org.apache.logging.log4j LogManager Level)
           (org.apache.logging.log4j.core LoggerContext)
           (org.apache.logging.log4j.core.config Configuration LoggerConfig)
           (clojure.lang ExceptionInfo)))

(defn set-log-level!
  "Changes the log level of the log4j2 root logger."
  [level & {:keys [logger-name]
            :or {logger-name LogManager/ROOT_LOGGER_NAME}}]
  (let [^Level level (Level/getLevel level)
        ^LoggerContext ctx (LogManager/getContext false)
        ^Configuration config (.getConfiguration ctx)
        ^LoggerConfig logger (.getLoggerConfig config logger-name)]
    (.setLevel logger level)
    (.updateLoggers ctx config)))

(def stups-logger-name "org.zalando.stups")

;; TODO This is mostly about setting logging levels, can better be done by a separate function
(defn run
  "Boots a whole new system."
  [{system-config :system} system]
  (log/info "Starting system...")

  (if-let [stups-log-level (:stups-log-level system-config)]
    (do
      (log/warn "Setting %s log level to %s." stups-logger-name stups-log-level)
      (set-log-level! stups-log-level :logger-name stups-logger-name)))

  (if-let [log-level (:log-level system-config)]
    (do
      (log/warn "Setting log level to %s." log-level)
      (set-log-level! log-level)))

  (try
    (let [system (component/start system)]
      (log/info "System started.")
      system)
    (catch ExceptionInfo e
      (when-let [{:as exd :keys [system]} (ex-data e)]
        (component/stop system))
      (throw e))))
