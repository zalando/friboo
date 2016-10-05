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
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.executor :as s1stexec]
            [io.sarnowski.swagger1st.util.api :as s1stapi]
            [io.sarnowski.swagger1st.util.api :as api]
            [io.sarnowski.swagger1st.util.newrelic :as newrelic]
            [org.zalando.stups.friboo.ring :as ring]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.friboo.system.metrics :refer [collect-swagger1st-zmon-metrics]]
            [org.zalando.stups.friboo.system.audit-log :refer [collect-audit-logs]]
            [org.zalando.stups.friboo.security :as security]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as r]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [clojure.data.codec.base64 :as b64]
            [io.clj.logging :refer [with-logging-context]]
            [clj-http.client :as client]
            [com.netflix.hystrix.core :refer [defcommand]]
            [clojure.core.memoize :as memo]
            [clojure.core.cache :as cache]
            [clojure.core.incubator :refer [dissoc-in]])
  (:import (com.netflix.hystrix.exception HystrixRuntimeException)
           (org.zalando.stups.txdemarcator Transactions)))

(defn flatten-parameters
  "According to the swagger spec, parameter names are only unique with their type. This one assumes that parameter names
   are unique in general and flattens them for easier access."
  [request]
  (apply merge (vals (:parameters request))))

(defn compute-request-info
  "Creates a nice, readable request info text for logline prefixing."
  [request]
  (str
    (.toUpperCase (-> request :request-method name))
    " "
    (:uri request)
    " <- "
    (if-let [x-forwarded-for (-> request :headers (get "x-forwarded-for"))]
      x-forwarded-for
      (:remote-addr request))
    (if-let [tokeninfo (:tokeninfo request)]
      (str " / " (get tokeninfo "uid") " @ " (get tokeninfo "realm"))
      "")))

(defn enrich-log-lines
  "Adds HTTP request context information to the logging facility's MDC in the 'request' key."
  [next-handler]
  (fn [request]
    (let [request-info (compute-request-info request)]
      (with-logging-context
        {:request (str " [" request-info "]")}
        (next-handler request)))))

(defn health-endpoint
  "Adds a /.well-known/health endpoint for load balancer tests."
  [handler]
  (fn [request]
    (if (= (:uri request) "/.well-known/health")
      (-> (r/response "{\"health\": true}")
          (ring/content-type-json)
          (r/status 200))
      (handler request))))

(defn- replace-auth
  [request new-value]
  (assoc-in request [:headers "authorization"] new-value))

(defn- parse-basic-auth
  "Parse HTTP Basic Authorization header"
  [authorization]
  (-> authorization
      (clojure.string/replace-first "Basic " "")
      .getBytes
      b64/decode
      String.
      (clojure.string/split #":" 2)
      (#(zipmap [:username :password] %))))

(defn map-authorization-header
  "Map 'Token' and 'Basic' Authorization values to standard Bearer OAuth2 auth"
  [authorization]
  (when authorization
    (condp #(.startsWith %2 %1) authorization
      "Token " (.replaceFirst authorization "Token " "Bearer ")
      "Basic " (let [basic-auth (parse-basic-auth authorization)]
                 (if (= (:username basic-auth) "oauth2")
                   (str "Bearer " (:password basic-auth))
                   ; do not touch Basic auth headers if username is not "oauth2"
                   authorization))
      authorization)))

(defn map-alternate-auth-header
  "Map alternate Authorization headers to standard OAuth2 'Bearer' auth"
  [handler]
  (fn [request]
    (let [authorization     (get-in request [:headers "authorization"])
          new-authorization (map-authorization-header authorization)]
      (if new-authorization
        (handler (replace-auth request new-authorization))
        (handler request)))))

(defn add-config-to-request
  "Adds the HTTP configuration to the request object, so that handlers can access it."
  [next-handler configuration]
  (fn [request]
    (next-handler (assoc request :configuration configuration))))

(defn convert-hystrix-exceptions
  [next-handler]
  (fn [request]
    (try
      (next-handler request)
      (catch HystrixRuntimeException e
        (let [reason       (-> e .getCause .toString)
              failure-type (str (.getFailureType e))]
          (log/warn (str "Hystrix: " (.getMessage e) " %s occurred, because %s") failure-type reason)
          (api/throw-error 503 (str "A dependency is unavailable: " (.getMessage e))))))))

(defn mark-transaction
  "Trigger the TransactionMarker with the swagger operationId for instrumentalisation."
  [next-handler]
  (fn [request]
    (let [operation-id (get-in request [:swagger :request "operationId"])
          tx-parent-id (get-in request [:headers Transactions/APPDYNAMICS_HTTP_HEADER])]
      (Transactions/runInTransaction operation-id tx-parent-id #(next-handler request)))))

(defn redirect-to-swagger-ui
  [& _]
  (ring.util.response/redirect "/ui/"))

(defn start-component
  "Starts the http component."
  [component metrics audit-logger definition resolver-fn]
  (if (:handler component)
    (do
      (log/debug "Skipping start of HTTP ; already running.")
      component)

    (do
      (log/info "Starting HTTP daemon for API %s" definition)
      (let [configuration (:configuration component)

            handler (-> (s1st/context :yaml-cp definition)
                        (s1st/ring wrap-gzip)
                        (s1st/ring enrich-log-lines)
                        (s1st/ring s1stapi/add-hsts-header)
                        (s1st/ring s1stapi/add-cors-headers)
                        (s1st/ring s1stapi/surpress-favicon-requests)
                        (s1st/ring health-endpoint)
                        (s1st/ring map-alternate-auth-header)
                        (s1st/discoverer)
                        (s1st/mapper)
                        (s1st/ring collect-swagger1st-zmon-metrics metrics)
                        (s1st/ring mark-transaction)
                        (newrelic/tracer)
                        (s1st/parser)
                        (s1st/ring convert-hystrix-exceptions)
                        (s1st/protector {"oauth2"
                                         (if (:tokeninfo-url configuration)
                                           (do
                                             (log/info "Checking access tokens against %s." (:tokeninfo-url configuration))
                                             (security/oauth2 configuration security/check-corresponding-attributes
                                                               :resolver-fn security/resolve-access-token))
                                           (do
                                             (log/warn "No token info URL configured; NOT ENFORCING SECURITY!")
                                             (security/allow-all)))})
                        (s1st/ring enrich-log-lines)        ; now we also know the user, replace request info
                        (s1st/ring add-config-to-request configuration)
                        (s1st/ring collect-audit-logs audit-logger)
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
      (when-not (:no-listen? (:configuration component))
        (.stop (:httpd component)))
      (merge component {:httpd   nil
                        :handler nil}))))

;; Resolver function takes a map {"operationId" "com.example.foo/bar"} and returns a function of request
;;  that calls (com.example.foo/bar params request ...)
;; In this case we want to call com.example.foo/bar with some additional parameters,
;;  with values already known at component start
;; These additional parameters are called dependencies and may be provided as individual arguments:
;;  (com.example.foo/bar params request db tokens)
;; Or as a map:
;;  (com.example.foo/bar params request {:db db :tokens tokens})

(defn make-resolver-fn-with-deps-as-args [_ dependency-values]
  (fn [request-definition]
    (if-let [operation-fn (s1stexec/operationId-to-function request-definition)]
      (fn [request]
        (apply operation-fn (flatten-parameters request) request dependency-values)))))

(defn make-resolver-fn-with-deps-as-map [dependency-names dependency-values]
  (let [dependency-map (zipmap (map keyword dependency-names) dependency-values)]
    (fn [request-definition]
      (if-let [operation-fn (s1stexec/operationId-to-function request-definition)]
        (fn [request]
          (operation-fn (flatten-parameters request) request dependency-map))))))

(defn select-resolver-fn-maker [{:keys [dependencies-as-map resolver-fn-maker]}]
  (cond
    dependencies-as-map make-resolver-fn-with-deps-as-map
    resolver-fn-maker resolver-fn-maker
    :default make-resolver-fn-with-deps-as-args))

(defmacro def-http-component
  "Creates an http component with your name and all your given dependencies. Those dependencies will also be available
   for your functions.

   Example:
     (def-http-component API \"example-api.yaml\" [db scheduler])

     (defn my-operation-function [parameters request db scheduler]
       ...)

  A flag can be provided to specify an alternative way of calling operation functions:
     (def-http-component API \"example-api.yaml\" [db scheduler] :dependencies-as-map true)

     (defn my-operation-function [parameters request {:keys [db scheduler]}]
       ...)

  You can specify a custom factory function for resolvers
     (def-http-component API \"example-api.yaml\" [db scheduler] :resolver-fn-maker (fn ...))
  Look at the existing implementations of make-resolver-fn-with-deps-as-args and make-resolver-fn-with-deps-as-map.

  The first parameter will be a flattened list of the request parameters. See the flatten-parameters function.
  "
  [name definition dependencies & {:as opts}]
  ; 'configuration' has to be given on initialization
  ; 'httpd' is the internal http server state
  `(defrecord ~name [~'configuration ~'httpd ~'metrics ~'audit-log ~@dependencies]
     Lifecycle

     (start [this#]
       (let [resolver-fn-maker# (select-resolver-fn-maker ~opts)
             resolver-fn# (resolver-fn-maker# '~dependencies ~dependencies)]
         (start-component this# ~'metrics ~'audit-log ~definition resolver-fn#)))

     (stop [this#]
       (stop-component this#))))
