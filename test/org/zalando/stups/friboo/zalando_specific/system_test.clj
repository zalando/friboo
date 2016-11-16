(ns org.zalando.stups.friboo.zalando-specific.system-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.zalando-specific.config :as config]
            [org.zalando.stups.friboo.zalando-specific.system.http :as http]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.test-utils :as u]
            [org.zalando.stups.friboo.system.metrics :as metrics]
            [org.zalando.stups.friboo.system :as system]
            [clj-http.client :as client]))

(defrecord Controller [configuration]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))

;; Example of the system that can be used in Zalando projects
(defn zalando-run [args-configuration]
  (let [configuration (config/load-config
                        (merge {}
                               args-configuration)
                        [:http :metrics :controller])
        system        (component/map->SystemMap
                        {:http       (component/using
                                       (http/make-zalando-http
                                         "org/zalando/stups/friboo/system/new_http.yml"
                                         (:http configuration)
                                         ;; TODO this is quite ugly, need to find a way to pass TOKENINFO_URL
                                         ;; to Http component
                                         (-> configuration :global :tokeninfo-url))
                                       [:metrics :controller])
                         :metrics    (metrics/map->Metrics {:configuration (:metrics configuration)})
                         :controller (map->Controller {:configuration (:controller configuration)})})]
    (system/run configuration system)))

(deftest works

  (let [system (zalando-run {:http-port     (u/get-free-port)
                             :tokeninfo-url "tokeninfo-url"})]
    (try
      ;; We just want to make sure that the whole system starts up successfully
      ;; Perform some basic smoke test
      (fact "tokeninfo-url is treated properly"
        (client/get (str "http://localhost:" (-> system :http :configuration :port) "/info")
                    {:throw-exceptions false})
        => (contains {:status 401}))
      (finally
        (component/stop system))))

  )
