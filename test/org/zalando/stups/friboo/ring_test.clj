(ns org.zalando.stups.friboo.ring-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.ring :refer :all]))

(deftest test-conpath
  (are [conpath-args expected-path]
    (= (apply conpath conpath-args) expected-path)

    ["https://example.com/" "test"] "https://example.com/test"
    ["https://example.com/" "/test"] "https://example.com/test"
    ["https://example.com" "/test"] "https://example.com/test"
    ["https://example.com" "test"] "https://example.com/test"
    ["https://example.com/" "test" "123"] "https://example.com/test/123"
    ["https://example.com/" 123 "test/foo" "bar"] "https://example.com/123/test/foo/bar"
    ["https://example.com/" 123 "/test"] "https://example.com/123/test"
    ["https://example.com/" nil "/test"] "https://example.com/test"))
