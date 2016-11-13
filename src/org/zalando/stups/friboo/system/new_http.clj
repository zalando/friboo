; Copyright © 2016 Zalando SE
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

(ns org.zalando.stups.friboo.system.new-http
  (:require [io.sarnowski.swagger1st.executor :as s1stexec]
            [io.clj.logging :refer [with-logging-context]]
            [ring.util.response :as r]
            [org.zalando.stups.friboo.ring :as ring]
            [clojure.data.codec.base64 :as b64]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [io.sarnowski.swagger1st.core :as s1st]
            [io.sarnowski.swagger1st.util.api :as s1stapi]
            [ring.adapter.jetty :as jetty]
            [org.zalando.stups.friboo.security :as security]
            [ring.middleware.gzip :as gzip])
  (:import (com.stuartsierra.component Lifecycle)
           (com.netflix.hystrix.exception HystrixRuntimeException)
           (org.zalando.stups.txdemarcator Transactions)
           (clojure.lang ExceptionInfo)))

(defn merged-parameters
  "According to the swagger spec, parameter names are only unique with their type. This one assumes that parameter names
   are unique in general and flattens them for easier access."
  [request]
  (apply merge (vals (:parameters request))))

(defn make-resolver-fn [controller]
  "Calls operationId function with controller, flattened request params and raw request map."
  (fn [request-definition]
    (when-let [operation-fn (s1stexec/operationId-to-function request-definition)]
      (fn [request]
        (operation-fn controller (merged-parameters request) request)))))

(defn middleware-chain [context middlewares]
  (reduce #(%2 %1) context middlewares))

(defn flatten1
  "Flattens the collection one level, for example, converts {:a 1 :b 2} to (:a 1 :b 2)."
  [coll]
  (apply concat coll))

(defn- with-flattened-options-map
  "If f is defined like this:

  `(defn f [first-arg & {:keys [a b] :as opts}] ... )`

  makes it convenient to pass `opts` as a map:

  `(-> first-arg (with-flattened-options-map f {:a 1 :b 2}))`
  will result in
  `(f first-arg :a 1 :b 2)`

  Used in `start-component` for clarity.
  "
  [first-arg f options]
  (apply f first-arg (flatten1 options)))

(defn start-component [{:as this :keys [api-resource configuration middlewares security-handlers controller s1st-options]}]
  (log/info "Starting HTTP daemon for API %s" api-resource)
  (when (:handler this)
    (throw (ex-info "Component already started, aborting." {})))
  ;; Refer to default-middlewares object below for middleware lists
  ;; Create context from the YAML definition on the classpath, optionally provide validation flag
  (let [handler (-> (apply s1st/context :yaml-cp api-resource (flatten1 (:context s1st-options)))
                    ;; Some middleware will need to access the component later
                    (assoc :component this)
                    ;; User can provide additional handlers to be executed before discoverer
                    (middleware-chain (:before-discoverer middlewares))
                    ;; Enables paths for API discovery, like /ui, /swagger.json and /.well-known/schema-discovery
                    (with-flattened-options-map s1st/discoverer (:discoverer s1st-options))
                    ;; User can provide additional handlers to be executed before mapper
                    (middleware-chain (:before-mapper middlewares))
                    ;; Given a path, figures out the spec part describing it
                    (s1st/mapper)
                    ;; User can provide additional handlers to be executed before protector
                    (middleware-chain (:before-protector middlewares))
                    ;; Enforces security according to the settings, depends on the spec from mapper
                    (s1st/protector (merge security/allow-all-handlers security-handlers))
                    ;; User can provide additional handlers to be executed before parser
                    (middleware-chain (:before-parser middlewares))
                    ;; Extracts parameter values from path, query and body of the request
                    (with-flattened-options-map s1st/parser (:parser s1st-options))
                    ;; User can provide additional handlers to be executed before executor
                    (middleware-chain (:before-executor middlewares))
                    ;; Calls the handler function for the request. Customizable through :resolver
                    (with-flattened-options-map s1st/executor (merge {:resolver (make-resolver-fn controller)}
                                                                     (:executor s1st-options))))]
    (merge this {:handler handler
                 :httpd   (jetty/run-jetty handler (merge configuration {:join? false}))})))

(defn stop-component
  "Stops the Http component."
  [{:as this :keys [handler httpd]}]
  (if-not handler
    (do
      (log/debug "Skipping stop of Http because it's not running.")
      this)
    (do
      (log/info "Stopping Http.")
      (when httpd
        (.stop httpd))
      (dissoc this :handler :httpd))))

(defrecord Http [;; parameters (filled in by make-http on creation)
                 api-resource
                 configuration
                 security-handlers
                 middlewares
                 s1st-options
                 ;; dependencies (filled in by the component library before starting)
                 controller
                 metrics
                 audit-log
                 ;; runtime vals (filled in by start-component)
                 httpd
                 handler]
  Lifecycle
  (start [this]
    (start-component this))
  (stop [this]
    (stop-component this)))

(defn redirect-to-swagger-ui
  "Can be used as operationId for GET /"
  [& _]
  (ring.util.response/redirect "/ui/"))

;; ## Middleware

(defn wrap-default-content-type [next-handler content-type]
  (fn [request]
    (let [response (next-handler request)]
      (if (get-in response [:headers "Content-Type"])
        response
        (assoc-in response [:headers "Content-Type"] content-type)))))

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

;; TODO Zalando specific
(defn map-authorization-header
  "Map 'Token' and 'Basic' Authorization values to standard Bearer OAuth2 auth."
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

;; TODO Zalando specific
(defn map-alternate-auth-header
  "Map alternate Authorization headers to standard OAuth2 'Bearer' auth"
  [handler]
  (fn [request]
    (let [authorization     (get-in request [:headers "authorization"])
          new-authorization (map-authorization-header authorization)]
      (if new-authorization
        (handler (assoc-in request [:headers "authorization"] new-authorization))
        (handler request)))))

(defn add-config-to-request
  "Adds the HTTP configuration to the request object, so that handlers can access it."
  [next-handler configuration]
  (fn [request]
    (next-handler (assoc request :configuration configuration))))

(defn convert-exceptions
  [next-handler]
  (fn [request]
    (try
      (next-handler request)
      ;; Hystrix exceptions as 503
      (catch HystrixRuntimeException e
        (let [reason       (-> e .getCause .toString)
              failure-type (str (.getFailureType e))]
          (log/warn (str "Hystrix: " (.getMessage e) " %s occurred, because %s") failure-type reason)
          (api/throw-error 503 (str "A dependency is unavailable: " (.getMessage e)))))
      ;; ex-info pass-through, will be caught inside parser
      (catch ExceptionInfo e
        (throw e))
      ;; Other exceptions as 500
      (catch Exception e
        (log/error e "Unhandled exception.")
        (api/throw-error 500 "Internal server error" (str e))))))

(defn mark-transaction
  "Trigger the TransactionMarker with the swagger operationId for instrumentalisation."
  [next-handler]
  (fn [request]
    (let [operation-id (get-in request [:swagger :request "operationId"])
          tx-parent-id (get-in request [:headers Transactions/APPDYNAMICS_HTTP_HEADER])]
      (Transactions/runInTransaction operation-id tx-parent-id #(next-handler request)))))

(def default-middlewares
  "Default set of ring middlewares that are groupped by s1st phases"
  {:before-discoverer [#(s1st/ring % add-config-to-request (-> % :component :configuration))
                       #(s1st/ring % gzip/wrap-gzip)
                       #(s1st/ring % enrich-log-lines)
                       #(s1st/ring % s1stapi/add-hsts-header)
                       #(s1st/ring % s1stapi/add-cors-headers)
                       #(s1st/ring % s1stapi/surpress-favicon-requests)
                       #(s1st/ring % health-endpoint)]
   :before-mapper     []
   :before-protector  []
   :before-parser     [#(s1st/ring % mark-transaction)]
   :before-executor   [;; Convert exceptions to ex-info so that parser creates nice 5xx responses
                       #(s1st/ring % convert-exceptions)
                       ;; now we also know the user, replace request info
                       #(s1st/ring % enrich-log-lines)
                       #(s1st/ring % wrap-default-content-type "application/json")]})

;; For documentation
(def default-s1st-options {;; It's possible to disable validation of the spec
                           ;; :context {:validate? true}
                           :context    {}
                           ;; The following parts are customizable:
                           ;; :discoverer {:discovery-path  "/.well-known/schema-discovery"
                           ;;              :definition-path "/swagger.json"
                           ;;              :ui-path         "/ui/"
                           ;;              :overwrite-host? true }
                           :discoverer {}
                           ;; No available options for parser, this is here for future
                           :parser     {}
                           ;; It's possible customize how exactly handler functions should be called
                           ;; :executor {:resolver io.sarnowski.swagger1st.executor/operationId-to-function}
                           :executor   {}})

(defn make-http
  "Creates Http component using mostly default parameters. No security, no additional middlewares."
  [api-resource configuration]
  (map->Http {:api-resource      api-resource
              :configuration     configuration
              :middlewares       default-middlewares
              :security-handlers {}                         ; No security by default
              :s1st-options      default-s1st-options}))
