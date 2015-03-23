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

(ns org.zalando.stups.friboo.system.http
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty :as jetty]
            [com.stuartsierra.component :refer [Lifecycle]]
            [org.zalando.stups.friboo.log :as log]
            [ring.util.response :as r]
            [org.zalando.stups.friboo.ring :as ring]
            [clojure.data.json :as json])
  (:import (clojure.lang ExceptionInfo)))

(defn flatten-parameters
  "According to the swagger spec, parameter names are only unique with their type. This one assumes that parameter names
   are unique in general and flattens them for easier access."
  [request]
  (apply merge (map (fn [[k v]] v) (:parameters request))))

(defn- add-ip-log-context
  "Adds the :client context to log statements."
  [handler]
  (fn [request]
    (log/log-with
      ; TODO make loadbalancer/proxy aware (forward-for header)
      {:ip (:remote-addr request)}
      (handler request))))

(defn- add-user-log-context
  "Adds the :user context to log statements."
  [handler]
  (fn [request]
    (log/log-with
      ; TODO add user information, noop currently
      {}
      (handler request))))

(def default-error
  (json/write-str
    {:message "Internal Server Error"}))

(defn- format-undefined-error [^Exception e]
  (-> (r/response default-error)
      (ring/content-type-json)
      (r/status 500)))

(defn- format-defined-error [^ExceptionInfo e]
  (let [data (-> (ex-data e)
                 (assoc :message (.getMessage e)))]
    (if-let [http-code (:http-code data)]
      (let [data (-> data
                     (dissoc :http-code))]
        (-> (r/response (json/write-str data))
            (ring/content-type-json)
            (r/status http-code)))
      (format-undefined-error e))))

(defn exceptions-to-json [handler]
  (fn [request]
    (try
      (handler request)
      (catch ExceptionInfo e
        (format-defined-error e))
      (catch Exception e
        (log/error e "Undefined exception during request execution: %s" (str e))
        (format-undefined-error e)))))

(defn start-component
  "Starts the http component."
  [component definition mapper-fn]
  (if (:httpd component)
    (do
      (log/debug "Skipping start of HTTP; already running.")
      component)

    (do
      (log/info "starting HTTP daemon for API" definition)
      (let [handler (-> (s1st/swagger-executor :mappers [mapper-fn])
                        (add-user-log-context)
                        (s1st/swagger-security)
                        (s1st/swagger-validator)
                        (s1st/swagger-parser)
                        (s1st/swagger-discovery)
                        (s1st/swagger-mapper ::s1st/yaml-cp definition
                                             :cors-origin (-> component :configuration :cors-origin))
                        (wrap-params)
                        (exceptions-to-json)
                        (add-ip-log-context))]

        (assoc component :httpd (jetty/run-jetty handler (merge (:configuration component)
                                                                {:join? false})))))))

(defn stop-component
  "Stops the http component."
  [component]
  (if-not (:httpd component)
    (do
      (log/debug "Skipping stop of HTTP; not running.")
      component)

    (do
      (log/info "Stopping HTTP daemon.")
      (.stop (:httpd component))
      (assoc component :httpd nil))))

(defmacro def-http-component
  "Creates an http component with your name and all your given dependencies. Those dependencies will also be available
   for your functions.

   Example:
     (def-http-component API \"example-api.yaml\" [db scheduler])

     (defn my-operation-function [parameters request db scheduler]
       ...)

  The first parameter will be a flattened list of the request parameters. See the flatten-parameters function.
  "
  [name definition dependencies]
  ; 'configuration' has to be given on initialization
  ; 'httpd' is the internal http server state
  `(defrecord ~name [~(symbol "configuration") ~(symbol "httpd") ~@dependencies]
     Lifecycle

     (start [this#]
       (let [mapper-fn# (fn [operationId#]
                          (if-let [cljfn# (s1st/map-function-name operationId#)]
                            (fn [request#]
                              (cljfn# (flatten-parameters request#) request# ~@dependencies))))]
         (start-component this# ~definition mapper-fn#)))

     (stop [this#]
       (stop-component this#))))
