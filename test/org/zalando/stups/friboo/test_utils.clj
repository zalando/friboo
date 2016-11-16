(ns org.zalando.stups.friboo.test-utils
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component])
  (:import (java.net ServerSocket)))

(defn track
  "Adds a tuple on call for an action."
  ([a action]
   (fn [& all-args]
     (swap! a conj {:key  action
                    :args (into [] all-args)}))))

(defn throwing
  "Returns a function that throws with the provided arguments when executed"
  [& [msg data]]
  (fn [& _]
    (throw (ex-info
             (or msg "any exception")
             (or data {})))))

(defmacro same!
  [x y]
  `(is (= ~x ~y)))

(defmacro true!
  [x]
  `(same! true ~x))

(defmacro false!
  [x]
  `(same! false ~x))

(defn get-free-port []
  (let [sock (ServerSocket. 0)]
    (try
      (.getLocalPort sock)
      (finally
        (.close sock)))))

(defmacro with-comp [[comp-sym comp-init] & body]
  `(let [~comp-sym (component/start ~comp-init)]
     (try
       ~@body
       (finally
         (component/stop ~comp-sym)))))
