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
(ns org.zalando.stups.friboo.system.mgmt-http
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.system.metrics :refer [add-metrics-servlet add-metrics-filter]]
            [ring.util.response :as r]
            [ring.adapter.jetty :as jetty]
            [ring.util.servlet :as servlet]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]])
  (:import (org.eclipse.jetty.servlet ServletHolder ServletContextHandler)
           (com.netflix.hystrix.contrib.metrics.eventstream HystrixMetricsStreamServlet)))

(def hystrix-dashboard-handler
  (-> (constantly (r/redirect "/monitor/monitor.html"))
      (wrap-resource "hystrix-dashboard")
      (wrap-content-type)
      (wrap-not-modified)))

(defn- add-hystrix-servlet
  [context]
  (.addServlet context (ServletHolder. HystrixMetricsStreamServlet) "/hystrix.stream")
  (.addServlet context (ServletHolder. (servlet/servlet hystrix-dashboard-handler)) "/")
  context)

(defn- mgmt-jetty-configurator
  [metrics]
  (fn [server] (-> (ServletContextHandler. server "/" ServletContextHandler/NO_SESSIONS)
                   (add-hystrix-servlet)
                   (add-metrics-servlet metrics)
                   (add-metrics-filter metrics))))

(defn run-mgmt-jetty
  "Starts Jetty with Hystrix event stream servlet"
  [metrics options]
  (jetty/run-jetty (constantly nil) (assoc options :configurator (mgmt-jetty-configurator metrics))))


(defn running? [component]
  (:mgmt-httpd component))

(defrecord MgmtHTTP [configuration metrics]
  Lifecycle

  (start [component]
    (if (running? component)
      ; then
      (do
        (log/info "Management HTTP server already running.")
        component)
      ; else
      (if (:no-listen? configuration)
        (do
          (log/info "Skip creation of management HTTP server. 'no-listen?' property found")
          component)
        (let [port (:port configuration 7979)
              server (run-mgmt-jetty metrics (merge configuration {:join? false
                                                                   :port  port}))]
          (log/info "Created a new management HTTP server")
          (assoc component :mgmt-httpd server)))))

  (stop [component]
    (if (running? component)
      ; then
      (do
        (.stop (:mgmt-httpd component))
        (log/info "Shut down the management HTTP server.")
        (dissoc component :mgmt-httpd))
      ; else
      (do
        (log/info "Management HTTP server not running.")
        component))))




