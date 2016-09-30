(ns org.zalando.stups.friboo.config-test
  (:require
    [clojure.test :refer :all]
    [midje.sweet :refer :all]
    [org.zalando.stups.friboo.config :refer :all]
    [amazonica.aws.kms :as kms]
    [environ.core :as environ])
  (:import [java.nio ByteBuffer]))

(deftest test-config-parse
  (is (= {:connect-timeout "123"} (:ldap (parse-namespaces {:ldap-connect-timeout "123"} [:ldap])))))

(deftest test-mask
  (is (= {:a "b" :password "MASKED" :private-stuff "MASKED" :my-secret-key "MASKED"}
         (mask {:a "b", :password "secret" :private-stuff "foobar" :my-secret-key "key"}))))

(deftest test-decrypt
  (is (= {:a "a" :b "b"} (decrypt {:a "a" :b "b"}))))

(deftest test-decrypt-value-with-aws-kms
  (with-redefs [kms/decrypt (constantly {:plaintext (-> "secret"
                                                        .getBytes
                                                        ByteBuffer/wrap)})]
    (is (= "secret" (decrypt-value-with-aws-kms "abc" "region-1")))))

(deftest wrap-midje-facts

  (facts "about load-configuration"

    (fact "If TOKENINFO_URL is not set, no remapping is done"
      (with-redefs [environ/env {}]
        (load-configuration [:tokeninfo :http] {:http-tokeninfo-url :tokeninfo-url} []))
      => {:system    {}
          :tokeninfo {}
          :http      {}})

    (fact "TOKENINFO_URL is implicitly duplicated as HTTP_TOKENINFO_URL"
      (with-redefs [environ/env {:tokeninfo-url ..tokeninfo-url..}]
        (load-configuration [:tokeninfo :http] {:http-tokeninfo-url :tokeninfo-url} []))
      => {:system    {}
          :tokeninfo {:url ..tokeninfo-url..}
          :http      {:tokeninfo-url ..tokeninfo-url..}})

    (fact "User's HTTP_TOKENINFO_URL takes precedence over the duplication"
      (with-redefs [environ/env {:tokeninfo-url      ..tokeninfo-url..
                                 :http-tokeninfo-url ..http-tokeninfo-url..}]
        (load-configuration [:tokeninfo :http] {:http-tokeninfo-url :tokeninfo-url} []))
      => {:system    {}
          :tokeninfo {:url ..tokeninfo-url..}
          :http      {:tokeninfo-url ..http-tokeninfo-url..}})

    (fact "Remapping is done even before namespacing"
      (with-redefs [environ/env {:tokeninfo-url ..tokeninfo-url..}]
        (load-configuration [:http] {:http-tokeninfo-url :tokeninfo-url} []))
      => {:system    {}
          :http      {:tokeninfo-url ..tokeninfo-url..}})

    (fact "By default, TOKENINFO_URL is remapped to HTTP_TOKENINFO_URL"
      (with-redefs [environ/env {:tokeninfo-url ..tokeninfo-url..}]
        (load-configuration [:http] []))
      => {:system    {}
          :http      {:tokeninfo-url ..tokeninfo-url..}})

    (fact "By default, TOKENINFO_URL is remapped to HTTP_TOKENINFO_URL, and HTTP_TOKENINFO_URL takes precedence too."
      (with-redefs [environ/env {:tokeninfo-url ..tokeninfo-url..
                                 :http-tokeninfo-url ..http-tokeninfo-url..}]
        (load-configuration [:http] []))
      => {:system    {}
          :http      {:tokeninfo-url ..http-tokeninfo-url..}}))

  )
