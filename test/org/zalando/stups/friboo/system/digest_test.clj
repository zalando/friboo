(ns org.zalando.stups.friboo.system.digest-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer [deftest]]
            [org.zalando.stups.friboo.system.digest :as d]))

(def test-string "foobar")
(def sha256 "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2")

(deftest test-digest
  (facts "digest"
    (fact "creates sha-256 hash"
      (d/digest test-string) => sha256)
    (fact "throws when input is nil or blank or not a string"
      (d/digest nil) => (throws AssertionError)
      (d/digest "") => (throws AssertionError)
      (d/digest {}) => (throws AssertionError)
      (d/digest []) => (throws AssertionError)
      (d/digest true) => (throws AssertionError)
      (d/digest false) => (throws AssertionError))))
