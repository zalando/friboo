(ns org.zalando.stups.friboo.system.audit-logger.http-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer [deftest]]
            [clj-http.client :as http]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.friboo.system.digest :as digest]
            [org.zalando.stups.friboo.system.audit-logger.http :as logger]))

(deftest test-http-logger
  (facts "HTTP logger"
    (let [log-fn (logger/logger-factory {:api-url "http://foo.bar"} .tokens.)]
      (fact "log function calls clj-http with provided url"
        (deref (log-fn {})) => nil
        (provided
          (digest/digest "{}") => "sha256"
          (oauth2/access-token :http-audit-logger .tokens.) => .token.
          (http/put "http://foo.bar/sha256" (contains {:body         "{}"
                                                       :oauth-token  .token.
                                                       :content-type :json})) => nil))
      (fact "log-factory creates single-arity function"
        (log-fn irrelevant irrelevant) => (throws Exception))
      (fact "log logs to stdout if http call fails"
        (deref (log-fn {})) => nil
        (provided
          (digest/digest "{}") => "sha256"
          (oauth2/access-token :http-audit-logger .tokens.) => .token.
          ; this is what friboo.log/error ultimately expands to
          (clojure.tools.logging/log* irrelevant :error irrelevant "Could not write audit event: [\"{}\"]") => nil :times 1
          (http/put "http://foo.bar/sha256" (contains {:body         "{}"
                                                       :oauth-token  .token.
                                                       :content-type :json})) =throws=> (new Exception "400 Bad Request"))))))
