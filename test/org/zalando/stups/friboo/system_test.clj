(ns org.zalando.stups.friboo.system-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.system.new-http :as http]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.test-utils :as u]))

(defn simple-run
  "Initializes and starts the whole system."
  [args-configuration]
  (let [configuration (config/load-config
                        (merge {}
                               args-configuration)
                        [:http])
        system        (component/map->SystemMap
                        {:http (http/make-http "org/zalando/stups/friboo/system/new_http.yml" (:http configuration))})]
    (component/start system)))

(deftest works

  (let [system (simple-run {:http-port (u/get-free-port)})]
    (try
      (fact "httpd is set"
        (-> system :http :httpd) => some?)
      (finally
        (component/stop system))))

  )
