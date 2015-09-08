(ns org.zalando.stups.friboo.system.db-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.system.db :refer :all]
            [clojure.data.json :as json]
            [clj-time.format :as f])
  (:import (java.sql Timestamp)
           (com.fasterxml.jackson.databind.util ISO8601Utils)
           (java.text ParsePosition)))

(deftest test-sql-timestamp-serialization
  (let [actual-millis (System/currentTimeMillis)
        timestamp (Timestamp. actual-millis)
        json (json/write-str timestamp)
        timestamp-string (json/read-str json)]
    (println "Milliseconds " actual-millis "have been serialized to " json)

    (are [parsed-millis]
      ; test, if serialized date can be parsed with different libs
      (= actual-millis parsed-millis)

      ; java 8 time
      (-> (java.time.ZonedDateTime/parse timestamp-string) .toInstant .toEpochMilli)
      ; joda time
      (-> (org.joda.time.DateTime/parse timestamp-string) .getMillis)
      ; clj-time default parser
      (-> (f/parse timestamp-string) .getMillis)
      ; clj-time date-time parser
      (-> (f/parse (f/formatters :date-time) timestamp-string) .getMillis)
      ; fasterxml jackson's ISO8601Utils
      (-> (ISO8601Utils/parse timestamp-string (ParsePosition. 0)) .getTime))))
