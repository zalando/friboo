(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.string :as str]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [clojure.test :refer [run-all-tests]]
            [{{namespace}}.core :as core]
            [org.zalando.stups.friboo.system :as system]
            [org.zalando.stups.friboo.dev :as dev]
            [taoensso.timbre :as log]))

;; A Var containing an object representing the application under development.
(defonce system nil)

(defn start
  "Starts the system running, sets the Var #'system."
  [extra-config]
  (alter-var-root #'system
                  (constantly
                   (core/run (merge (dev/load-dev-config "./dev-config.edn")
                                    extra-config)))))

(defn stop
  "Stops the system if it is currently running, updates the Var
  #'system."
  []
  (alter-var-root #'system
                  (fn [s] (when s (component/stop s)))))

(defn go
  "Initializes and starts the system running."
  ([extra-config]
   (start extra-config)
   :ready)
  ([]
   (go {})))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))

(defn run-tests []
  (run-all-tests #"{{namespace}}.*-test"))

(defn tests
  "Stops the system, reloads modified source files and runs tests"
  []
  (stop)
  (refresh :after 'user/run-tests))
