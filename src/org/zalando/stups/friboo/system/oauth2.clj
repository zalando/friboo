(ns org.zalando.stups.friboo.system.oauth2
  (:require [org.zalando.stups.friboo.config :refer [require-config]]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log])
  (:import (org.zalando.stups.tokens Tokens)
           (java.net URI)))


; configuration has to be given, contains links to IAM solution and timings
; tokens has to be given, contains map of tokenids to list of required scopes
; token-storage will be an atom containing the current access token
(defrecord OAUth2TokenRefresher [configuration tokens token-storage]
  component/Lifecycle

  (start [this]
    (let [access-token-url (require-config configuration :access-token-url)
          token-builder (Tokens/createAccessTokensWithUri (URI. access-token-url))]

      ; register all scopes
      (doseq [[token-id scopes] tokens]
        (let [token (.manageToken token-builder token-id)]
          (doseq [scope scopes]
            (.addScope token scope))
          (.done token)))

      (assoc this :token-storage (.start token-builder))))

  (stop [this]
    (.stop token-storage)
    (assoc this :access-tokens nil)))

(defn access-token
  "Returns the valid access token of the given ID."
  [token-id ^OAUth2TokenRefresher refresher]
  (let [token-storage (:token-storage refresher)]
    (.get token-storage token-id)))
