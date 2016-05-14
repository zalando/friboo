(ns org.zalando.stups.friboo.system.http-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.system.http :refer :all]))

(deftest test-flatten-parameters
  (is (= {:foo 1 :bar 2} (flatten-parameters {:parameters {:query {:foo 1} :path {:bar 2}}}))))

(deftest test-map-authorization-header-simple
  (is (= "xyz" (map-authorization-header "xyz")))
  (is (= "Bearer 123" (map-authorization-header "Token 123"))))

(deftest test-map-authorization-header-basic-auth
  (is (= "Bearer 123" (map-authorization-header "Basic b2F1dGgyOjEyMw=="))))
