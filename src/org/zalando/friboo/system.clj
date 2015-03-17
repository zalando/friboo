(ns org.zalando.friboo.system
  (:require [org.zalando.friboo.system.http :as http]
            [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [clojure.string :refer [replace-first]])
  (:import (org.apache.logging.log4j LogManager Level)
           (org.apache.logging.log4j.core LoggerContext)
           (org.apache.logging.log4j.core.config Configuration LoggerConfig)))

(defn- strip [namespace k]
  (keyword (replace-first (name k) (str (name namespace) "-") "")))

(defn- namespaced [config namespace]
  (if (contains? config namespace)
    (config namespace)
    (into {} (map (fn [[k v]] [(strip (name namespace) k) v])
                  (filter (fn [[k v]]
                            (.startsWith (name k) (str (name namespace) "")))
                          config)))))

(defn parse-namespaces [config namespaces]
  (let [namespaced-configs (into {} (map (juxt identity (partial namespaced config)) namespaces))]
    (doseq [[namespace namespaced-config] namespaced-configs]
      (log/debug "Destructured" namespace "into" namespaced-config))
    namespaced-configs))

(defn load-configuration
  "Loads configuration options from various places."
  [default-configuration & namespaces]
  (parse-namespaces
    (merge default-configuration env)
    (conj namespaces :system :http :db)))

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
