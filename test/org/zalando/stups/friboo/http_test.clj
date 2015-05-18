(ns org.zalando.stups.friboo.http-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.friboo.system.http :refer :all]))

(deftest test-map-authorization-header-simple
  (is (= "xyz" (map-authorization-header "xyz")))
  (is (= "Bearer 123" (map-authorization-header "Token 123"))))

(deftest test-map-authorization-header-basic-auth
  (is (= "Bearer 123" (map-authorization-header "Basic b2F1dGgyOjEyMw=="))))

