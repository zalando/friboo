(ns org.zalando.stups.friboo.system.db-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.system.db :refer :all]
            [cheshire.core :as json]
            [clj-time.format :as f]
            [org.zalando.stups.friboo.test-utils :as u]
            [com.stuartsierra.component :as component])
  (:import (java.sql Timestamp)
           (com.fasterxml.jackson.databind.util ISO8601Utils)
           (java.text ParsePosition)))

(deftest test-sql-timestamp-serialization
  (let [actual-millis (System/currentTimeMillis)
        timestamp (Timestamp. actual-millis)
        json (json/encode timestamp)
        timestamp-string (json/decode json)]
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

(deftest test-db-component-lifecycle
  (let [close-count (atom 0)
        db-component {:datasource (reify java.io.Closeable
                                    (close [this]
                                      (swap! close-count inc)))}
        stopped-db-component (stop-component db-component)]
    (is (= 1 @close-count))
    (stop-component stopped-db-component)
    (is (= 1 @close-count))))

(def test-db-config
  {:classname   "org.postgresql.Driver"
   :subprotocol "postgresql"
   :subname     "//localhost:5432/postgres"
   :user        "postgres"
   :password    "postgres"
   :init-sql    "SET statement_timeout TO '60s'; SET search_path TO test_data"})

(deftest wrap-midje-facts

  (u/with-comp [db-comp (map->DB {:configuration test-db-config})]
    (component/start db-comp)
    (prn db-comp))

  )

(generate-hystrix-commands)


(deftest test-load-flyway-configuration
  (let [configuration {:user           "user"
                       :password       "password"
                       :flyway.table   "tablename"
                       :flyway-schemas "schemas"}
        jdbc-url "jdbc-url"
        properties (load-flyway-configuration configuration jdbc-url)]
    (is (= properties {"flyway.password" "password"
                       "flyway.url"      "jdbc-url"
                       "flyway.driver"   ""
                       "flyway.user"     "user"
                       "flyway.table"    "tablename"
                       "flyway.schemas"  "schemas"}))))
