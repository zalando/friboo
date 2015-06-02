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
            [ring.util.servlet :as servlet]
            [com.stuartsierra.component :refer [Lifecycle]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [ring.util.response :as r]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [org.zalando.stups.friboo.ring :as ring]
            [clojure.data.codec.base64 :as b64]
            [io.clj.logging :refer [with-logging-context]]
            [io.sarnowski.swagger1st.util.api :as api]
            [clj-http.client :as client]
            [com.netflix.hystrix.core :refer [defcommand]]
            [overtone.at-at :as at]
            [amazonica.aws.s3 :as s3]
            [clojure.core.incubator :refer [dissoc-in]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as string]
            [clojure.data.json :as json])
  (:import (com.netflix.hystrix.contrib.metrics.eventstream HystrixMetricsStreamServlet)
           (org.eclipse.jetty.util.thread QueuedThreadPool)
           (org.eclipse.jetty.server Server)
           (org.eclipse.jetty.servlet ServletHolder ServletContextHandler)
           (com.netflix.hystrix.exception HystrixRuntimeException)
           (java.io ByteArrayInputStream)))

(defn flatten-parameters
  "According to the swagger spec, parameter names are only unique with their type. This one assumes that parameter names
   are unique in general and flattens them for easier access."
  [request]
  (apply merge (map (fn [[k v]] v) (:parameters request))))

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
    (let [authorization (get-in request [:headers "authorization"])
          new-authorization (map-authorization-header authorization)]
      (if new-authorization
        (handler (replace-auth request new-authorization))
        (handler request)))))

(defn add-config-to-request
  "Adds the HTTP configuration to the request object, so that handlers can access it."
  [next-handler configuration]
  (fn [request]
    (next-handler (assoc request :configuration configuration))))

(defcommand
  fetch-tokeninfo
  [tokeninfo-url access-token]
  (let [response (client/get tokeninfo-url
                             {:query-params     {:access_token access-token}
                              :throw-exceptions false
                              :as               :json-string-keys})]
    (if (client/server-error? response)
      (throw (IllegalStateException. (str "tokeninfo endpoint returned status code: " (:status response))))
      response)))

(defn resolve-access-token
  "Checks with a tokeninfo endpoint for the token's validity and returns the session information if valid."
  [tokeninfo-url access-token]
  (let [response (fetch-tokeninfo tokeninfo-url access-token)
        body (:body response)]
    (when (client/success? response) body)))

(defn convert-hystrix-exceptions
  [next-handler]
  (fn [request]
    (try
      (next-handler request)
      (catch HystrixRuntimeException e
        (let [reason (-> e .getCause .toString)
              failure-type (str (.getFailureType e))]
          (log/warn (str "Hystrix: " (.getMessage e) " %s occurred, because %s") failure-type reason)
          (api/throw-error 503 "A dependency is unavailable."))))))

(defn run-hystrix-jetty
  "Starts Jetty with Hystrix event stream servlet"
  [options]
  (let [^Server server (#'jetty/create-server (dissoc options :configurator))
        ^QueuedThreadPool pool (QueuedThreadPool. ^Integer (options :max-threads 50))]
    (when (:daemon? options false) (.setDaemon pool true))
    (doto server (.setThreadPool pool))
    (when-let [configurator (:configurator options)]
      (configurator server))
    (let [hystrix-holder (ServletHolder. HystrixMetricsStreamServlet)
          context (ServletContextHandler. server "/" ServletContextHandler/NO_SESSIONS)
          dashboard-handler (-> (constantly (r/redirect "/monitor/monitor.html"))
                                (wrap-resource "hystrix-dashboard")
                                (wrap-content-type)
                                (wrap-not-modified))]
      (.addServlet context hystrix-holder "/hystrix.stream")
      (.addServlet context (ServletHolder. (servlet/servlet dashboard-handler)) "/"))
    (.start server)
    (when (:join? options true) (.join server))
    server))

(defn add-logs
  "Adds a list of log entries to the audit logs container."
  [audit-logs logs]
  (dosync
    (alter audit-logs concat logs)))

(defn empty-logs
  "Returns all current audit logs and resets the container to empty."
  [audit-logs]
  (dosync
    (let [previous-logs @audit-logs]
      (ref-set audit-logs [])
      previous-logs)))

(defn is-modifying? [{:keys [request-method]}]
  (some #(= % request-method) [:post :put :patch :delete]))

(defn collect-audit-logs
  "Adds request map to audit-logs container."
  [next-handler audit-logs enabled?]
  (if enabled?
    (do
      (log/info "Logging all successful, modifying requests.")
      (fn [request]
        (let [{:keys [status] :as response} (next-handler request)]
          (when (and (>= status 200)
                     (< status 300)
                     (is-modifying? request))
            (let [audit-log (-> request
                                (assoc :logged-on (t/now))
                                (dissoc :swagger)
                                (dissoc :configuration)
                                (dissoc-in [:tokeninfo "access_token"]))]
              (add-logs audit-logs [audit-log])
              response)))))
    (do
      (log/info "Not logging modifying requests.")
      next-handler)))

(def audit-logs-file-format)

(def default-audit-flush-millis (* 5 60 1000))

(defn store-audit-logs!
  "Stores the audit logs in an S3 bucket."
  [audit-logs bucket]
  (let [previous-logs (empty-logs audit-logs)]
    (when (seq previous-logs)
      (try
        (let [file-content (string/join "\n" (map json/write-str previous-logs))
              file-stream (-> file-content (.getBytes "UTF-8") (ByteArrayInputStream.))
              content-length (count file-content)
              file-name (str (t/now))]
          (s3/put-object :bucket-name bucket
                         :key file-name
                         :input-stream file-stream
                         :metadata {:content-length content-length}))
        (catch Throwable t
          (add-logs audit-logs previous-logs)
          (log/warn "Could not store audit logs because of %s." (str t)))))))

(defn schedule-audit-log-flusher [bucket audit-logs configuration]
  (when bucket
    (let [flush-interval (or (:audit-flush-millis configuration) default-audit-flush-millis)
          pool (at/mk-pool :cpu-count 1)]
      (at/every flush-interval #(store-audit-logs! audit-logs bucket) pool :initial-delay flush-interval)
      pool)))

(defn stop-audit-log-flusher [bucket audit-logs pool]
  (when bucket
    (dosync
      (at/stop-and-reset-pool! pool :strategy :kill)
      (store-audit-logs! audit-logs bucket))))

(defn start-component
  "Starts the http component."
  [component definition resolver-fn]
  (if (:handler component)
    (do
      (log/debug "Skipping start of HTTP ; already running.")
      component)

    (do
      (log/info "Starting HTTP daemon for API" definition)
      (let [configuration (:configuration component)
            audit-logs-bucket (:audit-logs-bucket configuration)
            audit-logs (ref [])
            handler (-> (s1st/context :yaml-cp definition)
                        (s1st/ring enrich-log-lines)
                        (s1st/ring s1stapi/add-hsts-header)
                        (s1st/ring s1stapi/add-cors-headers)
                        (s1st/ring s1stapi/surpress-favicon-requests)
                        (s1st/ring health-endpoint)
                        (s1st/ring map-alternate-auth-header)
                        (s1st/discoverer)
                        (s1st/ring wrap-params)
                        (s1st/mapper)
                        (s1st/parser)
                        (s1st/ring convert-hystrix-exceptions)
                        (s1st/protector {"oauth2"
                                         (if (:tokeninfo-url configuration)
                                           (do
                                             (log/info "Checking access tokens against %s." (:tokeninfo-url configuration))
                                             (s1stsec/oauth-2.0 configuration s1stsec/check-corresponding-attributes
                                                                :resolver-fn resolve-access-token))
                                           (do
                                             (log/warn "No token info URL configured; NOT ENFORCING SECURITY!")
                                             (s1stsec/allow-all)))})
                        (s1st/ring enrich-log-lines)        ; now we also know the user, replace request info
                        (s1st/ring add-config-to-request configuration)
                        (s1st/ring collect-audit-logs audit-logs audit-logs-bucket)
                        (s1st/executor :resolver resolver-fn))]

        (merge component {:audit-logs-bucket audit-logs-bucket
                          :audit-logs        audit-logs})

        (if (:no-listen? configuration)
          (merge component {:httpd                nil
                            :hystrix-httpd        nil
                            :handler              handler
                            :audit-log-flush-pool nil})
          (merge component {:httpd                (jetty/run-jetty handler (merge configuration
                                                                                  {:join? false}))
                            :hystrix-httpd        (run-hystrix-jetty (merge configuration {:join? false
                                                                                           :port  7979}))
                            :handler              handler
                            :audit-log-flush-pool (schedule-audit-log-flusher audit-logs-bucket audit-logs configuration)}))))))

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
        (.stop (:httpd component))
        (.stop (:hystrix-httpd component))
        (stop-audit-log-flusher (:audit-logs-bucket component) (:audit-logs component) (:audit-log-flush-pool component)))
      (merge component {:httpd                nil
                        :hystrix-httpd        nil
                        :handler              nil
                        :audit-logs-bucket    nil
                        :audit-logs           nil
                        :audit-log-flush-pool nil}))))

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
