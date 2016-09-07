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

(def default-http-namespaces
  "Config namespaces, that are used by the default http component"
  [:http :tokeninfo :mgmt-http :audit-log :metrics])

(defn default-http-namespaces-and
  "Returns a vector, containing all default config namespaces for http components and the given additional ones"
  [& additional-ones]
  (apply conj default-http-namespaces additional-ones))

(defn http-system-map
  "Creates a system map for a typical http service, containing of
     - an API component (usually created with the org.zalando.stups.friboo.system.http/def-http-component)
     - a metrics component, to gather information about the frequency and duration of HTTP requests
     - an audit log component, to log successfull PUT, POST, PATCH & DELETE requests to an S3 bucket
     - a management HTTP server, that contains endpoints like '/metrics' and '/hystrix.stream' and runs on a different port

   Params:
   configuration - a map containing config entries grouped by namespace, can be obtained using friboo's config/load-configuration
   api-component-constructor - a function to obtain the api component. It will be called with one parameter: {:configuration (:http configuration)}
   api-dependencies - a vector containing the keys of all additional system dependencies used by the API component
   other-components - all additional system components as key-value pairs, as one would usually pass to component/system-map

   Example:
   Assuming your api component depends on on a 'db' and a 'http-audit-logger' component,
   a function to init and run a system could look like this:

       (defn run
         [default-configuration]
         (let [configuration (org.zalando.stups.friboo.config/load-configuration
                                (org.zalando.stups.friboo.system/default-http-namespaces-and :db :auditlogger)
                                [my-app.sql/default-db-configuration
                                 my-app.api/default-http-configuration
                                 default-configuration])
               system (org.zalando.stups.friboo.system/http-system-map configuration
                         my-app.api/map->API [:db :http-audit-logger]
                         :tokens (org.zalando.stups.friboo.system.oauth2/map->OAUth2TokenRefresher {:configuration (:oauth2 configuration)
                                                                                                    :tokens {:http-audit-logger [\"uid\"]}})
                         :http-audit-logger (component/using
                                              (org.zalando.stups.friboo.system.audit-logger.http/map->HTTP {:configuration (:auditlogger configuration)})
                                              [:tokens])
                         :db (my-app.sql/map->DB {:configuration (:db configuration)}))]

           (system/run configuration system)))
  "
  [configuration api-component-constructor api-dependencies & other-components]
  (apply component/system-map
         (concat [:api (component/using (api-component-constructor {:configuration (:http configuration)})
                                        (conj api-dependencies :metrics :audit-log))
                  :metrics (map->Metrics {:configuration (:metrics configuration)})
                  :audit-log (map->AuditLog {:configuration (:audit-log configuration)})
                  :mgmt-http (component/using (map->MgmtHTTP {:configuration (:mgmt-http configuration)}) [:metrics])]
                 other-components)))

(defn run
  "Boots a whole new system."
  [{system-config :system http-config :http} system]
  (log/info "Starting system...")

  (if-let [stups-log-level (:stups-log-level system-config)]
    (do
      (log/warn "Setting %s log level to %s." stups-logger-name stups-log-level)
      (set-log-level! stups-log-level :logger-name stups-logger-name)))

  (if-let [log-level (:log-level system-config)]
    (do
      (log/warn "Setting log level to %s." log-level)
      (set-log-level! log-level)))

  (when-not (:magnificent-url http-config)
    (log/warn "No configuration of magnificent, auth/get-auth will always return true!"))

  (let [system (component/start system)]

    (log/info "System started.")
    system))
