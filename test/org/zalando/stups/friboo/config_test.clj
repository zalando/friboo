(ns org.zalando.stups.friboo.config-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.friboo.config :refer :all]
    [amazonica.aws.kms :as kms])
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
