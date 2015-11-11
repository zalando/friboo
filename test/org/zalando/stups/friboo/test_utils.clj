(ns org.zalando.stups.friboo.test-utils
  (:require [clojure.test :refer :all]))

(defn track
  "Adds a tuple on call for an action."
  ([a action]
   (fn [& all-args]
     (swap! a conj {:key  action
                    :args (into [] all-args)}))))

(defmacro same!
  [x y]
  `(is (= ~x ~y)))

(defmacro not-same!
  [x y]
  `(is (not (= ~x ~y))))

(defmacro false!
  [x]
  `(same! false ~x))

(defmacro true!
  [x]
  `(same! true ~x))