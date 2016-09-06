(ns org.zalando.stups.friboo.system.audit-logger.s3-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer [deftest]]
            [amazonica.aws.s3 :as s3]
            [clj-time.format :as tf]
            [org.zalando.stups.friboo.system.digest :as digest]
            [org.zalando.stups.friboo.system.audit-logger.s3 :as logger]))

(deftest test-s3-logger
  (facts "S3 logger"
    (let [log-fn (logger/logger-factory {:s3-bucket .bucket-name.})]
      (fact "log function calls s3/put-object with provided bucket"
        (deref (log-fn {})) => nil
        (provided
          (tf/unparse irrelevant irrelevant) => "/path/to/file/"
          (digest/digest "{}") => "sha256"
          (s3/put-object
            :bucket-name .bucket-name.
            :key "/path/to/file/sha256"
            :metadata {:content-length 2
                       :content-type   "application/json"}
            :input-stream irrelevant) => nil))
      (fact "log-factory creates single-arity function"
        (log-fn irrelevant irrelevant) => (throws Exception))
      (fact "log logs to stdout if s3 call fails"
        (deref (log-fn {})) => nil
        (provided
          (tf/unparse irrelevant irrelevant) => "/path/to/file/"
          (digest/digest "{}") => "sha256"
          ; this is what friboo.log/error ultimately expands to
          (clojure.tools.logging/log* irrelevant :error irrelevant "Could not write audit event: [\"{}\"]") => nil :times 1
          (s3/put-object
            :bucket-name .bucket-name.
            :key "/path/to/file/sha256"
            :metadata {:content-length 2
                       :content-type   "application/json"}
            :input-stream irrelevant) =throws=> (new Exception "400 Bad Request"))))))
