(ns org.zalando.friboo.system
  (:require [org.zalando.friboo.system.http :as http]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log])
  (:import (org.apache.logging.log4j LogManager Level)
           (org.apache.logging.log4j.core LoggerContext)
           (org.apache.logging.log4j.core.config Configuration LoggerConfig)))

(defn load-configuration
  "Loads configuration options from various places."
  []
  ; yes, that's enough for now
  env)

(defn set-log-level
  "Changes the log level of the log4j2 root logger."
  [level]
  (let [^Level level (Level/getLevel level)
        ^LoggerContext ctx (LogManager/getContext false)
        ^Configuration config (.getConfiguration ctx)
        ^LoggerConfig logger (.getLoggerConfig config LogManager/ROOT_LOGGER_NAME)]
    (.setLevel logger level)
    (.updateLoggers ctx)))

(defn new-system
  "Creates a new system-map that preconfigures an HTTP server with swagger1st."
  [configuration system]
  (let [default-system {:http (component/using (http/new-http configuration) [:api])
                        :api  (http/new-default-api)}]
    (component/map->SystemMap (merge default-system system))))

(defn run
  "Boots a whole new system."
  [configuration system]
  (if-let [log-level (:log-level configuration)]
    (do
      (log/warn "setting log level to" log-level)
      (set-log-level log-level)))
  (let [system (new-system configuration system)]
    (log/trace "starting system")
    (component/start system)))
