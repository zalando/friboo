(ns org.zalando.stups.friboo.auth-test
  (:require
    [clj-http.client :as http]
    [org.zalando.stups.friboo.test-utils :refer :all]
    [org.zalando.stups.friboo.auth :as auth]
    [clojure.test :refer :all]))

(deftest fetch-auth
  (testing "should return true if response was OK"
    (with-redefs [http/get (constantly {:status 200})]
      (let [result (auth/fetch-auth "magnificent-url" "policy" "team" "token")]
        (true! result))))
  (testing "should return false if response was not OK"
    (with-redefs [http/get (constantly {:status 403})]
      (let [result (auth/fetch-auth "magnificent-url" "policy" "team" "token")]
        (false! result))))
  (testing "should return false in case of error"
    (with-redefs [http/get (throwing "UAAAH")]
      (let [result (auth/fetch-auth "magnificent-url" "policy" "team" "token")]
        (false! result)))))

(deftest require-auth
  (testing "should throw if access is denied"
    (with-redefs [auth/get-auth (constantly false)]
      (try
        (auth/require-auth {} "team")
        (is false)
        (catch Exception ex
          (let [data (ex-data ex)]
            (same! 403 (:http-code data)))))))
  (testing "should not throw if access is granted"
    (with-redefs [auth/get-auth (constantly true)]
      (let [result (auth/require-auth {} "team")]
        (is (nil? result))))))
