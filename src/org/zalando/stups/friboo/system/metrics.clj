(ns org.zalando.stups.friboo.system.metrics
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [org.zalando.stups.friboo.log :as log]
            [metrics.core :as metrics]
            [metrics.timers :as tmr]
            [clojure.string :as string]
            [org.zalando.stups.friboo.config :as config])
  (:import (java.util.concurrent TimeUnit)
           (com.codahale.metrics.servlets MetricsServlet$ContextListener MetricsServlet)
           (org.eclipse.jetty.servlet ServletHolder FilterHolder ServletContextHandler)
           (java.util EnumSet)
           (javax.servlet DispatcherType Filter ServletRequest ServletResponse FilterChain FilterConfig)
           (javax.servlet.http HttpServletResponse HttpServletRequest)))

(defn- segment2str
  [x]
  (if (keyword? x)
    (str "{" (name x) "}")
    (str x)))

(defn- path2str
  [coll]
  (string/join "." (map segment2str coll)))

(defn swagger1st-request2string
  "GET /apps/{application_id} -> 200.GET.apps.{application_id}"
  [request status]
  (clojure.string/join "." [status
                            (.toUpperCase (-> request :request-method name))
                            (-> request :swagger :key second path2str)]))

(defn http-servlet-request2string
  "GET /hystrix.stream -> 200.GET.hystrix.stream"
  [^HttpServletRequest request status]
  (clojure.string/join "." [status
                            (.toUpperCase (.getMethod request))
                            (let [path (string/replace (subs (.getServletPath request) 1) #"[/]" ".")]
                              (if (or (string/blank? path) (= path "."))
                                "*ROOT*"
                                path))]))

(defn collect-swagger1st-request-metrics
  "Ring middleware that creates Timers for Swagger API calls
  and stores them in the running metrics component."
  [next-handler {:keys [metrics-registry configuration]}]
  (if metrics-registry
    (fn [request]
      (let [start    (System/currentTimeMillis)
            response (next-handler request)
            status   (:status response)
            prefix   (config/require-config configuration :metrics-prefix)
            timer    (tmr/timer metrics-registry [prefix "response" (swagger1st-request2string request status)])]
        (.update timer (- (System/currentTimeMillis) start) (TimeUnit/MILLISECONDS))
        response))
    (do
      (log/info "Not collecting metrics for requests. Metrics component is not running.")
      next-handler)))

(defn running? [component]
  (:metrics-registry component))

(defn add-metrics-servlet
  [context metrics]
  (when-let [metrics-registry (:metrics-registry metrics)]
    (.addEventListener context (proxy [MetricsServlet$ContextListener] []
                                 (getMetricRegistry [] metrics-registry)
                                 (getDurationUnit [] TimeUnit/MILLISECONDS)
                                 (getAllowedOrigin [] "*")))
    (.addServlet context (ServletHolder. MetricsServlet) "/metrics"))
  context)

(defn add-metrics-filter
  [^ServletContextHandler context {:keys [metrics-registry configuration]}]
  (when metrics-registry
    (.addFilter
      context
      (FilterHolder.
        (proxy [Filter] []
          (doFilter [^ServletRequest request ^ServletResponse response ^FilterChain chain]
            (if (and (instance? HttpServletResponse response) (instance? HttpServletRequest request))
              (let [start  (System/currentTimeMillis)
                    status (atom 500)]
                (try
                  (.doFilter chain request response)
                  (swap! status (fn [_] (.getStatus (cast HttpServletResponse response))))

                  (finally
                    (let [prefix      (config/require-config configuration :metrics-prefix)
                          request-str (http-servlet-request2string request @status)
                          timer       (tmr/timer metrics-registry [prefix "response" request-str])]
                      (.update timer (- (System/currentTimeMillis) start) (TimeUnit/MILLISECONDS))))))
              (.doFilter chain request response)))

          (init [^FilterConfig _] nil)
          (destroy [] nil)))
      "/metrics"
      (EnumSet/of DispatcherType/REQUEST)))
  context)

(def default-configuration
  {:metrics-prefix "friboo"})

(defrecord Metrics [configuration]
  Lifecycle

  (start [component]
    (if (running? component)
      ; then
      (do
        (log/info "Metrics registry already running.")
        component)
      ; else
      (let [registry                    (metrics/new-registry)
            configuration-with-defaults (merge default-configuration configuration)]
        (log/info "Created a new metrics registry.")
        (assoc component :metrics-registry registry
                         :configuration configuration-with-defaults))))


  (stop [component]
    (if (running? component)
      ; then
      (do
        (log/info "Shutting down the metrics registry.")
        (dissoc component :metrics-registry))
      ; else
      (do
        (log/info "Metrics registry not running.")
        component))))
