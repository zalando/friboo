; Copyright © 2015 Zalando SE
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.zalando.stups.friboo.system
  (:require [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.system.metrics :refer [map->Metrics]]
            [org.zalando.stups.friboo.system.audit-log :refer [map->AuditLog]]
            [org.zalando.stups.friboo.system.mgmt-http :refer [map->MgmtHTTP]]
            )
  (:import (org.apache.logging.log4j LogManager Level)
           (org.apache.logging.log4j.core LoggerContext)
           (org.apache.logging.log4j.core.config Configuration LoggerConfig)))

(defn- set-log-level!
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

(defn http-system-map
  ""
  [configuration api-component-constructor api-dependencies & other-components]
  (apply component/system-map
         (concat [:api (component/using (api-component-constructor {:configuration (:http configuration)})
                                        (conj api-dependencies :metrics :audit-log))
                  :metrics (map->Metrics {})
                  :audit-log (map->AuditLog {:configuration (:audit-log configuration)})
                  :mgmt-http (component/using (map->MgmtHTTP {:configuration (:http configuration)}) [:metrics])]
                 other-components)))

(defn run
  "Boots a whole new system."
  [{configuration :system} system]
  (log/info "Starting system...")

  (if-let [stups-log-level (:stups-log-level configuration)]
    (do
      (log/warn "Setting %s log level to %s." stups-logger-name stups-log-level)
      (set-log-level! stups-log-level :logger-name stups-logger-name)))

  (if-let [log-level (:log-level configuration)]
    (do
      (log/warn "Setting log level to %s." log-level)
      (set-log-level! log-level)))

  (let [system (component/start system)]

    (log/info "System started.")
    system))
