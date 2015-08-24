(ns org.zalando.stups.friboo.user-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.friboo.user :refer :all]
    [org.zalando.stups.friboo.test-utils :refer :all]
    [clj-http.client :as http])
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

(deftest test-get-teams
  (let [calls (atom [])]
    (with-redefs [http/get (comp (constantly {:body [{:name "team1"} {:name "team2"}]})
                                 (track calls :http-get))]
      (is (= (get-teams "https://teams.example.org" "TOKEN12345" "a-user") [{:name "team1"} {:name "team2"}]))
      (is (= (count @calls) 1))
      (is (= (:args (first @calls))
             ["https://teams.example.org/api/accounts/aws" {:query-params {:member "a-user"}
                                                            :oauth-token "TOKEN12345"
                                                            :as :json}])))))