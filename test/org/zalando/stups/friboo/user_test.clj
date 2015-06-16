(ns org.zalando.stups.friboo.user-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.friboo.user :refer :all])
  (:import (clojure.lang ExceptionInfo)))

(deftest test-trim-slashes
  (are [input expected-output] (= (trim-realm input) expected-output)
                               "employees" "employees"
                               "/employees" "employees"
                               "employees/" "employees"
                               "/employees/" "employees"
                               "//employees//" "employees"
                               "/" "/"
                               "employees/tech" "employees/tech"
                               "/employees/tech" "employees/tech"
                               "employees/tech/" "employees/tech"
                               "/employees/tech/" "employees/tech"))

(deftest test-trim-slashes-resilience-empty
  (is (= "" (trim-realm ""))))

(deftest test-trim-slashes-resilience-nil
  (is (= nil (trim-realm nil))))

(deftest test-require-realm
  (is (= "employees" (require-realms #{"employees"} {:tokeninfo {"realm" "employees"}}))))

(deftest test-require-realm-with-leading-slash
  (is (= "employees" (require-realms #{"employees"} {:tokeninfo {"realm" "/employees"}}))))

(deftest test-require-missing-realm
  (let [ex (is (thrown? ExceptionInfo (require-realms #{"services"} {:tokeninfo {"realm" "employees"}})))]
    (is (= (-> ex .getData :http-code) 403))))
