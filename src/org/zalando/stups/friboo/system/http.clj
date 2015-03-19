(ns org.zalando.stups.friboo.system.http
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.adapter.jetty :as jetty]
            [com.stuartsierra.component :refer [Lifecycle]]
            [clojure.tools.logging :as log]))

(defn flatten-parameters
  "According to the swagger spec, parameter names are only unique with their type. This one assumes that parameter names
   are unique in general and flattens them for easier access."
  [request]
  (apply merge (map (fn [[k v]] v) (:parameters request))))

(defn start-component
  "Starts the http component."
  [component definition mapper-fn]
  (if (:httpd component)
    (do
      (log/debug "skipping start of HTTP; already running")
      component)

    (do
      (log/info "starting HTTP daemon for API" definition)
      (let [handler (-> (s1st/swagger-executor :mappers [mapper-fn])
                        (s1st/swagger-security)
                        (s1st/swagger-validator)
                        (s1st/swagger-parser)
                        (s1st/swagger-discovery)
                        (s1st/swagger-mapper ::s1st/yaml-cp definition
                                             :cors-origin (-> component :configuration :cors-origin))
                        (wrap-params))]

        (assoc component :httpd (jetty/run-jetty handler (merge (:configuration component)
                                                                {:join? false})))))))

(defn stop-component
  "Stops the http component."
  [component]
  (if-not (:httpd component)
    (do
      (log/debug "skipping stop of HTTP; not running")
      component)

    (do
      (log/info "stopping HTTP daemon")
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
