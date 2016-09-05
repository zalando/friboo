(ns org.zalando.stups.friboo.system.digest-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer [deftest]]
            [org.zalando.stups.friboo.system.digest :as d]))

(def test-string "foobar")
(def sha256 "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2")
(def sha1 "8843d7f92416211de9ebb963ff4ce28125932878")
(def md5 "3858f62230ac3c915f300c664312c63f")

(deftest test-digest
  (facts "digest"
    (fact "creates sha-256 hash by default"
      (d/digest test-string) => sha256)
    (fact "uses provided algorithm"
      (d/digest test-string "MD5") => md5
      (d/digest test-string "SHA-1") => sha1
      (d/digest test-string "SHA-256") => sha256)
    (fact "throws when an unsupported algorithm is provided"
      (d/digest test-string "SHA-2") => (throws AssertionError))
    (fact "throws when input is nil or blank or not a string"
      (d/digest nil) => (throws AssertionError)
      (d/digest "") => (throws AssertionError)
      (d/digest {}) => (throws AssertionError)
      (d/digest []) => (throws AssertionError)
      (d/digest true) => (throws AssertionError)
      (d/digest false) => (throws AssertionError))))
