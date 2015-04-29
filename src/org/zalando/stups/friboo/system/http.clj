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
            [io.sarnowski.swagger1st.executor :as s1stexec]
            [io.sarnowski.swagger1st.util.api :as s1stapi]
            [io.sarnowski.swagger1st.util.security :as s1stsec]
            [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty :as jetty]
            [com.stuartsierra.component :refer [Lifecycle]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [ring.util.response :as r]
            [org.zalando.stups.friboo.ring :as ring]))

(defn flatten-parameters
  "According to the swagger spec, parameter names are only unique with their type. This one assumes that parameter names
   are unique in general and flattens them for easier access."
  [request]
  (apply merge (map (fn [[k v]] v) (:parameters request))))

; TODO doesn't work currently
(defn- add-ip-log-context
  "Adds the :client context to log statements."
  [handler]
  (fn [request]
    (log/log-with
      ; TODO make loadbalancer/proxy aware (forward-for header)
      {:ip (:remote-addr request)}
      (handler request))))

; TODO doesn't work currently
(defn- add-user-log-context
  "Adds the :user context to log statements."
  [handler]
  (fn [request]
    (log/log-with
      ; TODO add user information, noop currently
      {}
      (handler request))))

(defn health-endpoint
  "Adds a /.well-known/health endpoint for load balancer tests."
  [handler]
  (fn [request]
    (if (= (:uri request) "/.well-known/health")
      (-> (r/response "{\"health\": true}")
          (ring/content-type-json)
          (r/status 200))
      (handler request))))

(defn start-component
  "Starts the http component."
  [component definition resolver-fn]
  (if (:handler component)
    (do
      (log/debug "Skipping start of HTTP ; already running.")
      component)

    (do
      (log/info "starting HTTP daemon for API" definition)
      (let [configuration (:configuration component)
            handler (-> (s1st/context :yaml-cp definition)
                        (s1st/ring add-ip-log-context)
                        (s1st/ring s1stapi/add-hsts-header)
                        (s1st/ring s1stapi/add-cors-headers)
                        (s1st/ring s1stapi/surpress-favicon-requests)
                        (s1st/ring health-endpoint)
                        (s1st/discoverer)
                        (s1st/ring wrap-params)
                        (s1st/mapper)
                        (s1st/parser)
                        (s1st/protector {"oauth2" (s1stsec/allow-all)}) ; TODO do correct implementation
                        (s1st/ring add-user-log-context)
                        (s1st/executor :resolver resolver-fn))]

        (if (:no-listen? configuration)
          (merge component {:httpd   nil
                            :handler handler})
          (merge component {:httpd   (jetty/run-jetty handler (merge configuration
                                                                     {:join? false}))
                            :handler handler}))))))

(defn stop-component
  "Stops the http component."
  [component]
  (if-not (:handler component)
    (do
      (log/debug "Skipping stop of HTTP; not running.")
      component)

    (do
      (log/info "Stopping HTTP daemon.")
      (.stop (:httpd component))
      (merge component {:httpd   nil
                        :handler nil}))))

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
       (let [resolver-fn# (fn [request-definition#]
                            (if-let [cljfn# (s1stexec/operationId-to-function request-definition#)]
                              (fn [request#]
                                (cljfn# (flatten-parameters request#) request# ~@dependencies))))]
         (start-component this# ~definition resolver-fn#)))

     (stop [this#]
       (stop-component this#))))
