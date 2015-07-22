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
(ns org.zalando.stups.friboo.system.metrics
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [org.zalando.stups.friboo.log :as log]
            [metrics.core :as metrics]
            [metrics.timers :as tmr]
            [clojure.string :as string])
  (:import (java.util.concurrent TimeUnit)
           (com.codahale.metrics.servlets MetricsServlet$ContextListener MetricsServlet)
           (org.eclipse.jetty.servlet ServletHolder)))

(defn- segment2str
  [x]
  (if (keyword? x)
    (str "{" (name x) "}")
    (str x)))

(defn- path2str
  [coll]
  (string/join "." (map segment2str coll)))

(defn request2string
  "GET /apps/{application_id} -> 200.GET.apps.{application_id}"
  [request status]
  (clojure.string/join "." [status
                            (.toUpperCase (-> request :request-method name))
                            (-> request :swagger :key second path2str)]))

(defn collect-zmon-metrics
  "Ring middleware that creates Timers for Swagger API calls
  and stores them in the running metrics component."
  [next-handler component]
  (if-let [metrics-registry (:metrics-registry component)]
    ; then
    (fn [request]
      (let [start (System/currentTimeMillis)
            response (next-handler request)
            status (:status response)
            timer (tmr/timer metrics-registry ["zmon" "response" (request2string request status)])]
        (.update timer (- (System/currentTimeMillis) start) (TimeUnit/MILLISECONDS))
        response))
    ; else
    (do
      (log/info "Do not collect metrics for Zmon. The metrics component is not running.")
      next-handler)))

(defn running? [component]
  (:metrics-registry component))

(defn add-metrics-servlet
  [context metrics]
  (when-let [metrics-registry (:metrics-registry metrics)]
    (.addEventListener context (proxy [MetricsServlet$ContextListener] []
                                 (getMetricRegistry [] metrics-registry)
                                 (getRateUnit [] TimeUnit/MILLISECONDS)
                                 (getDurationUnit [] TimeUnit/MILLISECONDS)
                                 (getAllowedOrigin [] "*")))
    (.addServlet context (ServletHolder. MetricsServlet) "/metrics"))
  context)

(defrecord Metrics []
  Lifecycle

  (start [component]
    (if (running? component)
      ; then
      (do
        (log/info "Metrics registry already running.")
        component)
      ; else
      (let [registry (metrics/new-registry)]
        (log/info "Created a new metrics registry.")
        (assoc component :metrics-registry registry))))


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
