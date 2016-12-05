(ns org.zalando.stups.friboo.ring-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.ring :refer :all]))

(deftest wrap-midje-facts

  (facts "about content-type-json"
    (content-type-json {}) => {:headers {"Content-Type" "application/json"}})

  (facts "about single-response"
    (single-response []) => (contains {:status 404})
    (single-response ["foo"]) => (contains {:status 200 :body "foo"}))

  )
