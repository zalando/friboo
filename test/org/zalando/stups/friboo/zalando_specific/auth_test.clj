(ns org.zalando.stups.friboo.zalando-specific.auth-test
  (:require
    [clj-http.client :as http]
    [org.zalando.stups.friboo.test-utils :refer :all]
    [org.zalando.stups.friboo.zalando-specific.auth :as auth]
    [clojure.test :refer :all]
    [org.zalando.stups.friboo.test-utils :as u]))

(deftest fetch-auth
  (u/with-comp [auth-comp (auth/map->Authorizer {:configuration {:magnificent-url    "magnificent-url"
                                                                 :magnificent-policy "policy"}})]
    (testing "should return true if response was OK"
      (with-redefs [http/get (constantly {:status 200})]
        (let [result (auth/fetch-auth auth-comp "team" "token")]
          (true! result))))
    (testing "should return false if response was not OK"
      (with-redefs [http/get (constantly {:status 403})]
        (let [result (auth/fetch-auth auth-comp "team" "token")]
          (false! result))))
    (testing "should return false in case of error"
      (with-redefs [http/get (throwing "UAAAH")]
        (let [result (auth/fetch-auth auth-comp "team" "token")]
          (false! result))))))

(deftest require-auth
  (with-comp [auth-comp (auth/map->Authorizer {:configuration {:magnificent-url    "magnificent-url"
                                                               :magnificent-policy "policy"}})]
    (testing "should throw if access is denied"
      (with-redefs [auth/fetch-auth (constantly false)]
        (try
          (auth/require-auth auth-comp {} "team")
          (is false)
          (catch Exception ex
            (let [data (ex-data ex)]
              (same! 403 (:http-code data)))))))
    (testing "should not throw if access is granted"
      (with-redefs [auth/fetch-auth (constantly true)]
        (let [result (auth/require-auth auth-comp {} "team")]
          (is (nil? result))))))
  (with-comp [auth-comp (auth/map->Authorizer {:configuration {}})]
    (testing "When magnificent-url is not set, should return true no matter what fetch-auth would say"
      (with-redefs [auth/fetch-auth (constantly false)]
        (same! true (auth/get-auth auth-comp {} "team"))))))
