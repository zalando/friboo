(ns org.zalando.stups.friboo.zalando-specific.system.http-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.zalando-specific.system.http :refer :all :as http]
            [clojure.pprint :refer [pprint]]))

(deftest wrap-midje-facts

  (facts "about parse-basic-auth"
    (#'http/parse-basic-auth "Basic Zm9vOmJhcg==") => {:password "bar", :username "foo"})

  (facts "about map-authorization-header"
    (map-authorization-header "Token foobar") => "Bearer foobar"
    (map-authorization-header "Basic b2F1dGgyOmZvb2Jhcg==") => "Bearer foobar")

  )
