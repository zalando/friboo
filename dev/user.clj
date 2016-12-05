(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
    [clojure.java.javadoc :refer [javadoc]]
    [clojure.pprint :refer [pprint]]
    [clojure.reflect :refer [reflect]]
    [clojure.repl :refer [apropos dir doc find-doc pst source]]
    [clojure.tools.namespace.repl :refer [refresh refresh-all]]
    [clojure.test :refer [run-all-tests]]))

(defn run-tests []
  (run-all-tests #"org.zalando.stups.friboo.*-test"))

(defn tests
  "Stops the system, reloads modified source files and runs tests"
  []
  (refresh :after 'user/run-tests))
