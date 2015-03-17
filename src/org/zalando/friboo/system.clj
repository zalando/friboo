(ns org.zalando.friboo.system
  (:require [org.zalando.friboo.system.http :as http]
            [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log])
  (:import (org.apache.logging.log4j LogManager Level)
           (org.apache.logging.log4j.core LoggerContext)
           (org.apache.logging.log4j.core.config Configuration LoggerConfig)))


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
  (let [default-system {:http (component/using (http/new-http (:http configuration)) [:api])
                        :api  (http/new-default-api)}]
    (component/map->SystemMap (merge default-system system))))

(defn run
  "Boots a whole new system."
  [{configuration :system} system]
  (log/info "starting system...")

  (if-let [log-level (:log-level configuration)]
    (do
      (log/warn "setting log level to" log-level)
      (set-log-level log-level)))

  (log/trace "starting system")
  (let [system (component/start system)]

    (log/info "system started.")
    system))
