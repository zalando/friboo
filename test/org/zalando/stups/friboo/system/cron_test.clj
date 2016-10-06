(ns org.zalando.stups.friboo.system.cron-test
  (:require [org.zalando.stups.friboo.system.cron :refer :all]
            [clojure.test :refer :all]
            [overtone.at-at :as at]
            [com.stuartsierra.component :as component]))

(def-cron-component TestCron [state]
  (at/at (at/now) (job deliver state 42) pool))

(deftest test-cron-component-lifecycle
  ;; Here we make sure that the component is started and stopped properly.
  (let [state (promise)
        cron-component (map->TestCron {:state state})]
    (-> cron-component
        component/start
        component/stop
        component/stop) ; stopping twice shouldn't break anything
    (is (= 42 (deref state 500 :not-delivered)))))