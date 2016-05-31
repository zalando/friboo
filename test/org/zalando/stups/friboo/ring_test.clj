(ns org.zalando.stups.friboo.ring-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.ring :refer :all])
  (:import (clojure.lang ExceptionInfo)))

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

(deftest test-with-json-handler
  (testing "Wraps and rethrows"
    (is (thrown? ExceptionInfo #"Internal server error"
                 (with-json-handler
                   (/ 1 0)))))
  (testing "Adds Content-Type header"
    (is (= {:status  200
            :body    "ok"
            :headers {"Content-Type" "application/json"}}
           (with-json-handler
             {:status 200
              :body   "ok"})))))
