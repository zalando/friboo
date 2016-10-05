(ns org.zalando.stups.friboo.system.oauth2-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.system.oauth2 :refer :all]
            [com.stuartsierra.component :as component])
  (:import (org.zalando.stups.tokens AccessTokens AccessTokenUnavailableException)))

(defn create-access-tokens-mock [token-map]
  (reify AccessTokens
    (get [_ token-id]
      (get token-map token-id))
    (stop [_])))

(deftest wrap-midje-facts

  (facts "about parse-static-tokens"
    (parse-static-tokens "token1=foo,token2,token3=bar") => {:token1 "foo" :token3 "bar"})

  (facts "about OAuth2TokenRefresher"
    (with-redefs [AccessTokensBuilder-start (fn [_]
                                              (create-access-tokens-mock {:bar "real-bar" :baz "real-baz"}))]
      (let [refresher (component/start (map->OAuth2TokenRefresher
                                         {:configuration {:access-token-url "access-token-url"
                                                          :credentials-dir  "credentials-dir"
                                                          :access-tokens    "foo=fake-foo,baz=fake-baz"}
                                          :tokens        {:bar ["a"] :baz ["b"]}}))]
        (fact "can retrieve static token"
          (access-token :foo refresher) => "fake-foo")
        (fact "can retrieve normal token"
          (access-token :bar refresher) => "real-bar")
        (fact "static token takes precedence"
          (access-token :baz refresher) => "fake-baz")
        (fact "if the token was not registered, throws an exception"
          (access-token :nonono refresher) => (throws AccessTokenUnavailableException))
        (fact "stopping"
          (component/stop refresher) => (contains {:token-storage nil})))))

  )
