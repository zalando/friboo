(ns org.zalando.stups.friboo.zalando-specific.config-test
  (:require
    [clojure.test :refer :all]
    [midje.sweet :refer :all]
    [org.zalando.stups.friboo.zalando-specific.config :refer :all]
    [amazonica.aws.kms :as kms]
    [environ.core :as environ])
  (:import [java.nio ByteBuffer]))

(deftest wrap-midje-facts

  (facts "about load-configuration"

    (fact "By default, TOKENINFO_URL CREDENTIAL_DIR are duplicated into HTTP_ and OAUTH2_"
      (with-redefs [environ/env {:tokeninfo-url   ..tokeninfo-url..
                                 :credentials-dir ..credentials-dir..}]
        (load-config {} []))
      => {:system {}
          :global {:tokeninfo-url ..tokeninfo-url..}
          :oauth2 {:credentials-dir ..credentials-dir..}})

    (fact "Decryption works"
      (with-redefs [environ/env {:db-password "aws:kms:foo"}]
        (load-config {} [:db]))
      => {:system {}
          :db     {:password "secret"}
          :global {}
          :oauth2 {}}
      (provided
        (kms/decrypt anything anything) => {:plaintext (-> "secret" .getBytes ByteBuffer/wrap)}))
    )

  )
