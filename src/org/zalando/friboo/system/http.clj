(ns org.zalando.friboo.system.http
  (:require [io.sarnowski.swagger1st.core :as s1st]
            [ring.middleware.params :refer [wrap-params]]
            [ring.util.response :as r]
            [com.stuartsierra.component :as component]
            [org.httpkit.server :as httpkit]
            [clojure.tools.logging :as log]))

(defprotocol API
  (get-mapper-fn [this] "provides a mapper function to resolve functions"))

; 'configuration' will be given on initialization
; 'httpd' is the internal http server state
; 'api' has to be injected before start and must contain an API protocol implementation
(defrecord HTTP [configuration httpd api]
  component/Lifecycle

  (start [this]
    (if httpd
      (do
        (log/debug "skipping start of HTTP; already running")
        this)

      (do
        (let [{:keys [definition cors-origin]} configuration]
          (log/info "starting HTTP daemon for API" definition)
          (let [handler (-> (s1st/swagger-executor :mappers [(get-mapper-fn api)])
                            (s1st/swagger-security)
                            (s1st/swagger-validator)
                            (s1st/swagger-parser)
                            (s1st/swagger-discovery)
                            (s1st/swagger-mapper ::s1st/yaml-cp definition :cors-origin cors-origin)
                            (wrap-params))]

            ; use httpkit as ring implementation
            (assoc this :httpd (httpkit/run-server handler configuration)))))))

  (stop [this]
    (if-not httpd
      (do
        (log/debug "skipping stop of HTTP; not running")
        this)

      (do
        (log/info "stopping HTTP daemon")
        (httpd :timeout 100)
        (assoc this :httpd nil)))))

(defn new-http
  "Official constructor for the HTTP."
  [configuration]
  (map->HTTP {:configuration configuration}))

(defn flattened-parameter-mapper
  "swagger1st mapper that flattens the given parameters and provides them simply as a map in the first argument."
  [operationId]
  (fn [request]
    (if-let [cljfn (s1st/map-function-name operationId)]
      (let [flattened-parameters (reduce merge (:parameters request))]
        (cljfn flattened-parameters request)))))

; If your API function do not need any other component, this component can be used.
(defrecord DefaultApi [] component/Lifecycle API
  (get-mapper-fn [_] flattened-parameter-mapper))

(defn new-default-api []
  (map->DefaultApi {}))
