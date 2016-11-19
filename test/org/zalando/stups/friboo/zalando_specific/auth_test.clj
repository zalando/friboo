(ns org.zalando.stups.friboo.zalando-specific.auth-test
  (:require
    [clj-http.client :as http]
    [org.zalando.stups.friboo.test-utils :refer :all]
    [org.zalando.stups.friboo.zalando-specific.auth :refer :all]
    [clojure.test :refer :all]
    [midje.sweet :refer :all]))

(deftest test-fetch-auth
  (with-comp [auth-comp (map->Authorizer {:configuration {:magnificent-url    "magnificent-url"
                                                          :magnificent-policy "policy"}})]
    (testing "should return true if response was OK"
      (with-redefs [http/get (constantly {:status 200})]
        (let [result (fetch-auth auth-comp "token" {:payload "team"})]
          (true! result))))
    (testing "should return false if response was not OK"
      (with-redefs [http/get (constantly {:status 403})]
        (let [result (fetch-auth auth-comp "token" {:payload "team"})]
          (false! result))))
    (testing "should return false in case of error"
      (with-redefs [http/get (throwing "UAAAH")]
        (let [result (fetch-auth auth-comp "token" {:payload "team"})]
          (false! result))))))

(deftest test-require-auth
  (with-comp [auth-comp (map->Authorizer {:configuration {:magnificent-url    "magnificent-url"
                                                          :magnificent-policy "policy"}})]
    (testing "should throw if access is denied"
      (with-redefs [fetch-auth (constantly false)]
        (try
          (require-auth auth-comp {} {:payload "team"})
          (is false)
          (catch Exception ex
            (let [data (ex-data ex)]
              (same! 403 (:http-code data)))))))
    (testing "should not throw if access is granted"
      (with-redefs [fetch-auth (constantly true)]
        (let [result (require-auth auth-comp {} {:payload "team"})]
          (is (nil? result))))))
  (with-comp [auth-comp (map->Authorizer {:configuration {}})]
    (testing "When magnificent-url is not set, should return true no matter what fetch-auth would say"
      (with-redefs [fetch-auth (constantly false)]
        (same! true (get-auth auth-comp {} "team"))))))

(deftest wrap-midje-facts

  (facts "about get-auth"
    (with-comp [auth-comp (map->Authorizer {:configuration {:magnificent-policy "policy"}})]
      (fact "When magnificent-url is not set, just return true"
        (get-auth auth-comp anything anything) => true
        (provided
          (http/get anything anything) => nil :times 0)))
    (with-comp [auth-comp (map->Authorizer {:configuration {:magnificent-url    "magnificent-url"
                                                            :magnificent-policy "policy"}})]
      (fact "Should call POST magnificent-url/auth with payload and auth header"
        (get-auth auth-comp {"access_token" "token"} {:team "team"}) => true
        (provided
          (http/get "magnificent-url/auth" (contains {:oauth-token "token"
                                                      :form-params {:policy  "policy"
                                                                    :payload {:team "team"}}}))
          => {:status 200}))))

  )
