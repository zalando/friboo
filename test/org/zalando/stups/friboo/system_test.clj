(ns org.zalando.stups.friboo.system-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system.http :as http]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.test-utils :as u]
            [org.zalando.stups.friboo.system :as system])
  (:import (clojure.lang ExceptionInfo)))

(defn simple-run
  "Initializes and starts the whole system."
  [args-configuration]
  (let [configuration (config/load-config
                        (merge {}
                               args-configuration)
                        [:http])
        system        (component/map->SystemMap
                        {:http (http/make-http "org/zalando/stups/friboo/system/http.yml" (:http configuration))})]
    (component/start system)))

(defrecord FakeComponent [throw-error? counter initialized]
  component/Lifecycle
  (start [this]
    (when throw-error?
      (throw (ex-info "error" {})))
    (swap! counter inc)
    (assoc this :initialized true))
  (stop [this]
    (when initialized
      (swap! counter dec))
    (assoc this :initialized false)))

(defn error-run [counter]
  (let [system (component/map->SystemMap
                 {:comp1 (map->FakeComponent {:counter counter})
                  :comp2 (component/using
                           (map->FakeComponent {:counter counter :throw-error? true})
                           [:comp1])})]
    (system/run {} system)))

(deftest works

  (let [system (simple-run {:http-port (u/get-free-port)})]
    (try
      (fact "After the system starts, httpd is set"
        (-> system :http :httpd) => some?)
      (finally
        (component/stop system))))

  (facts "when one of the components fails to start, the system is shut down"
    (let [counter (atom 0)]
      (fact "system throws an error"
        (error-run counter)) => (throws ExceptionInfo)
      (fact "counter should be 0"
        @counter => 0)))

  )
