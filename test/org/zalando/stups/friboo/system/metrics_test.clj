(ns org.zalando.stups.friboo.system.metrics-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.system.metrics :refer :all]
            [org.zalando.stups.friboo.test-utils :refer [track]]
            [metrics.core :as metrics])
  (:import (org.eclipse.jetty.servlet ServletContextHandler ServletHolder)
           (java.util.concurrent TimeUnit)))

(defn mock-request [method path]
  {:swagger        {:key [method (seq path)]}
   :request-method method})

(deftest test-request2string
  (are [request status expected-string]
    (= (swagger1st-request2string request status) expected-string)
    (mock-request :get ["apps" :app_id "credentials"]) 200 "200.GET.apps.{app_id}.credentials"
    (mock-request :post ["apps"]) 400 "400.POST.apps"))

(deftest test-collect-zmon-metrics
  (let [component (.start (map->Metrics {:configuration {:metrics-prefix "foo"}}))
        next-handler (constantly {:status 404 :body "not found"})
        handler (collect-swagger1st-request-metrics next-handler component)]
    (with-redefs [swagger1st-request2string (constantly "mock")]
      (handler "a request"))
    (let [timer (-> component :metrics-registry .getTimers (get "foo.response.mock"))]
      (is (not (nil? timer))))))

(deftest test-collect-zmon-metrics-component-not-running
  (let [component (map->Metrics {})
        next-handler (constantly {:status 404 :body "not found"})
        handler (collect-swagger1st-request-metrics next-handler component)]
    (is (= handler next-handler))))

(deftest test-add-metrics-servlet
  (let [calls (atom [])
        context (proxy [ServletContextHandler] []
                  (addEventListener [listener]
                    ((track calls :add-listener) listener))
                  (addServlet [^ServletHolder _ ^String _]
                    ((track calls :add-servlet))))
        component (.start (map->Metrics {}))]
    (add-metrics-servlet context component)
    (let [add-listener-calls (filter #(= :add-listener (:key %)) @calls)
          add-servlet-calls (filter #(= :add-servlet (:key %)) @calls)]
      (is (= 1 (count add-listener-calls)))
      (let [listener (-> add-listener-calls first :args first)]
        (is (not (nil? listener)))
        (is (= (.getMetricRegistry listener) (:metrics-registry component)))
        (is (nil? (.getRateUnit listener)))
        (is (= (.getDurationUnit listener) TimeUnit/MILLISECONDS))
        (is (= (.getAllowedOrigin listener) "*")))
      (is (= 1 (count add-servlet-calls))))))

(deftest test-add-metrics-servlet-when-component-not-running
  (let [calls (atom [])
        context (proxy [ServletContextHandler] []
                  (addEventListener [listener]
                    ((track calls :add-listener) listener))
                  (addServlet [^ServletHolder _ ^String _]
                    ((track calls :add-servlet))))
        component (map->Metrics {})]
    (add-metrics-servlet context component)
    (is (empty? @calls))))

(deftest test-component-lifecycle
  (let [calls (atom [])
        component (atom (map->Metrics {}))]
    (with-redefs [metrics/new-registry (track calls :new-registry)]
      ; stop not-running component
      (swap! component (fn [c] (.stop c)))
      (is (empty? @calls))
      (is (not (running? @component)))
      (swap! calls empty)

      ; start component the first time
      (swap! component (fn [c] (.start c)))
      (is (= 1 (count (filter #(= :new-registry (:key %)) @calls))))
      (is (running? @component))
      (swap! calls empty)

      ; start component twice should not have an effect
      (swap! component (fn [c] (.start c)))
      (is (empty? @calls))
      (is (running? @component))
      (swap! calls empty)

      ; stop component
      (swap! component (fn [c] (.stop c)))
      (is (empty? @calls))
      (is (not (running? @component)))
      (swap! calls empty))))
