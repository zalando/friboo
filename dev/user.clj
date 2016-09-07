(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
    [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [clojure.edn :as edn]
    [clojure.test :refer [run-all-tests]]))

(def system
  "A Var containing an object representing the application under
  development."
  nil)

(defn slurp-if-exists [file]
  (when (.exists (clojure.java.io/as-file file))
    (slurp file)))

(defn load-dev-config [file]
  (edn/read-string (slurp-if-exists file)))

(defn run-tests []
  (run-all-tests #"org.zalando.stups.friboo.*-test"))

(defn tests
  "Stops the system, reloads modified source files and runs tests"
  []
  (refresh :after 'user/run-tests))
