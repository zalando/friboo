(ns org.zalando.stups.friboo.run-test
  (:require
    [clojure.test :refer :all]
    [environ.core :refer [env]]
    [com.stuartsierra.component :refer [Lifecycle]]
    [org.zalando.stups.friboo.config :as config]
    [org.zalando.stups.friboo.system :as system]
    [org.zalando.stups.friboo.system.http :as http]
    [org.zalando.stups.friboo.test-utils :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(def default-configuration
  {:foo "bar"})

(def default-http-configuration
  {:port 8080})

(defrecord API
  [configuration httpd metric audit-log]
  Lifecycle
  (start [this]
    (println "Started"))
  (stop [this]
    (println "Stopped")))

(defn run
 [default-configuration]
 (let [configuration (config/load-configuration
                        system/default-http-namespaces
                        [default-http-configuration
                         default-configuration])
       system (system/http-system-map configuration map->API [])]
   (is (= "default-tokeninfo"
          (get-in configuration [:http :tokeninfo-url])))
   (system/run configuration
               system)))

(deftest test-run
  (run default-configuration))
