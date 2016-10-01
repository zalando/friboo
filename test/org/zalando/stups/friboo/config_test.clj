(ns org.zalando.stups.friboo.config-test
  (:require
    [clojure.test :refer :all]
    [midje.sweet :refer :all]
    [org.zalando.stups.friboo.config :refer :all]
    [org.zalando.stups.friboo.config-decrypt :refer :all]
    [amazonica.aws.kms :as kms]
    [environ.core :as environ])
  (:import [java.nio ByteBuffer]))

(deftest test-config-parse
  (is (= {:connect-timeout "123"} (:ldap (parse-namespaces {:ldap-connect-timeout "123"} [:ldap]))))
  (is (= {:connect-timeout "123"} (:ldap (parse-namespaces {:ldap-connect-timeout "123" :ldapfoo "bar"} [:ldap])))))

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

  (facts "about load-config"

    (fact "If TOKENINFO_URL is not set, no remapping is done"
      (with-redefs [environ/env {}]
        (load-config nil [:http] {:mapping {:http-tokeninfo-url :tokeninfo-url}}))
      => {:system {}
          :http   {}})

    (fact "TOKENINFO_URL is duplicated as HTTP_TOKENINFO_URL"
      (with-redefs [environ/env {:tokeninfo-url ..tokeninfo-url..}]
        (load-config nil [:http] {:mapping {:http-tokeninfo-url :tokeninfo-url}}))
      => {:system {}
          :http   {:tokeninfo-url ..tokeninfo-url..}})

    (fact "User's HTTP_TOKENINFO_URL takes precedence over the duplication"
      (with-redefs [environ/env {:tokeninfo-url      ..tokeninfo-url..
                                 :http-tokeninfo-url ..http-tokeninfo-url..}]
        (load-config nil [:http] {:mapping {:http-tokeninfo-url :tokeninfo-url}}))
      => {:system {}
          :http   {:tokeninfo-url ..http-tokeninfo-url..}})
    )

  (facts "about load-configuration"

    (fact "By default, TOKENINFO_URL CREDENTIAL_DIR are duplicated into HTTP_ and OAUTH2_"
      (with-redefs [environ/env {:tokeninfo-url   ..tokeninfo-url..
                                 :credentials-dir ..credentials-dir..}]
        (load-configuration [:http :oauth2] []))
      => {:system {}
          :http   {:tokeninfo-url ..tokeninfo-url..}
          :oauth2 {:credentials-dir ..credentials-dir..}})

    (fact "Decryption works"
      (with-redefs [environ/env {:db-password "aws:kms:foo"}]
        (load-configuration [:db] []))
      => {:system {}
          :db     {:password "secret"}}
      (provided
        (kms/decrypt anything anything) => {:plaintext (-> "secret" .getBytes ByteBuffer/wrap)}))
    )

  )
