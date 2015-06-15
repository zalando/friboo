(ns org.zalando.stups.friboo.user-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.friboo.user :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(deftest test-trim-slashes
  (doseq [test-string ["employees" "/employees" "employees/" "/employees/"]]
    (is (= "employees" (trim-slashes test-string)))))

(deftest test-trim-slashes-resilience-empty
  (is (= "" (trim-slashes ""))))

(deftest test-trim-slashes-resilience-nil
  (is (= nil (trim-slashes nil))))

(deftest test-require-realm
  (is (= "employees" (require-realms #{"employees"} {:tokeninfo {"realm" "employees"}}))))

(deftest test-require-realm-with-leading-slash
  (is (= "employees" (require-realms #{"employees"} {:tokeninfo {"realm" "/employees"}}))))

(deftest test-require-missing-realm
  (let [ex (is (thrown? ExceptionInfo (require-realms #{"services"} {:tokeninfo {"realm" "employees"}})))]
    (is (= (-> ex .getData :http-code) 403))))
