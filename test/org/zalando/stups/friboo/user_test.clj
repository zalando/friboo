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
                                                            :as :json}]))
      (is (= (get-teams "https://teams.example.org" "TOKEN12345" "a-user") [{:name "team1"} {:name "team2"}]))
      ; we should have a cache hit, i.e. no HTTP call this time..
      (is (= (count @calls) 1)))))

(deftest test-require-service-team
  (testing "it should throw without user id"
    (let [calls (atom [])]
      (with-redefs [get-service-team (track calls :service-user)]
        (try
          (require-service-team "team-britney"
                                {:tokeninfo {}
                                 :configuration {:service-user-url "service-api"}})
          (is false)
          (catch ExceptionInfo ex
            (let [data (ex-data ex)]
              (same! 0 (count @calls))
              (same! 403 (:http-code data))
              (same! (:message data)
                     "no user information available")))))))

  (testing "it should throw if robot is in no team"
    (let [calls (atom [])]
      (with-redefs [get-service-team (comp (constantly "")
                                           (track calls :service-user))]
        (try
          (require-service-team "team-broforce"
                                "robobro"
                                {}
                                "service-api")
          (is false)
          (catch ExceptionInfo ex
            (let [data (ex-data ex)]
              (same! 1 (count @calls))
              (same! :service-user (:key (first @calls)))
              (same! 403 (:http-code data))
              (true! (.contains (:message data) "user has no teams"))))))))


  (testing "it should throw if robot is not in correct team"
    (let [calls (atom [])]
      (with-redefs [get-service-team (comp (constantly "team-terrorists")
                                           (track calls :service-user))]
        (try
          (require-service-team "team-broforce"
                                "robobro"
                                {}
                                "service-api")
          (is false)
          (catch ExceptionInfo ex
            (let [data (ex-data ex)]
              (same! 1 (count @calls))
              (same! :service-user (:key (first @calls)))
              (same! 403 (:http-code data))
              (true! (.contains (:message data) "user not in team team-broforce"))))))))

  (testing "it should return the team if everything is fine"
    (let [calls (atom [])]
      (with-redefs [get-service-team (comp (constantly "team-broforce")
                                           (track calls :service-user))]
          (let [team (require-service-team "team-broforce"
                                           "robobro"
                                           {}
                                           "service-api")]
            (same! team "team-broforce"))))))
