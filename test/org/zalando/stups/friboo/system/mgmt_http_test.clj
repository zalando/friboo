(ns org.zalando.stups.friboo.system.mgmt-http-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.system.mgmt-http :refer :all]
            [org.zalando.stups.friboo.test-utils :refer [track]]
            [org.zalando.stups.friboo.log :as log])
  (:import (org.eclipse.jetty.util.component LifeCycle)))

(deftest test-no-listen-start-component
  (let [component (.start (map->MgmtHTTP {:configuration {:no-listen? true}}))]
    (is (not (running? component)))))

(deftest test-component-lifecycle
  (let [configuration {:foo        "bar"
                       :no-listen? false}
        metrics {:name "a metrics component"}
        calls (atom [])
        component (atom (map->MgmtHTTP {:configuration configuration
                                        :metrics       metrics}))
        mock-server (proxy [LifeCycle] []
                      (stop []
                        (log/warn "STOP has been called")
                        ((track calls :stop-jetty))))]
    (with-redefs [run-mgmt-jetty (comp (constantly mock-server) (track calls :run-jetty))]
      ; stop not-running component
      (swap! component (fn [c] (.stop c)))
      (is (empty? @calls))
      (is (not (running? @component)))
      (swap! calls empty)

      ; start component the first time
      (swap! component (fn [c] (.start c)))
      (let [run-calls (filter #(= :run-jetty (:key %)) @calls)
            metrics-param (first (:args (first run-calls)))
            options-param (second (:args (first run-calls)))]
        (is (= 1 (count run-calls)))
        (is (= metrics-param metrics))
        (is (= options-param {:foo "bar" :no-listen? false :join? false :port 7979})))
      (is (running? @component))
      (swap! calls empty)

      ; start component twice should not have an effect
      (swap! component (fn [c] (.start c)))
      (is (empty? @calls))
      (is (running? @component))
      (swap! calls empty)

      ; stop component
      (swap! component (fn [c] (.stop c)))
      (is (= 1 (count (filter #(= :stop-jetty (:key %)) @calls))))
      (is (not (running? @component)))
      (swap! calls empty))))
