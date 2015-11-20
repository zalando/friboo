(ns org.zalando.stups.friboo.system.oauth2
  (:require [org.zalando.stups.friboo.config :refer [require-config]]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [clj-http.client :as client]
            [clojure.core.cache :as cache]
            [clojure.core.memoize :as memo]
            [com.netflix.hystrix.core :refer [defcommand]]
            [org.zalando.stups.friboo.log :as log])
  (:import (org.zalando.stups.tokens Tokens)
           (java.net URI)
           (java.io IOException)))


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


(defn- unauthorized
  "Send forbidden response."
  [reason]
  (log/warn "ACCESS DENIED (unauthorized) because %s." reason)
  (api/error 401 "Unauthorized"))

(defn- unauthorized-without-logging
  "Send forbidden response."
  [reason]
  (api/error 401 "Unauthorized"))

(defn- forbidden
  "Send forbidden response."
  [reason]
  (log/warn "ACCESS DENIED (forbidden) because %s." reason)
  (api/error 403 "Forbidden"))

(defn allow-all
  "Allows everything."
  []
  (fn [request definition requirements]
    request))

(defn- extract-access-token
  "Extracts the bearer token from the Authorization header."
  [request]
  (if-let [authorization (get-in request [:headers "authorization"])]
    (when (.startsWith authorization "Bearer ")
      (.substring authorization (count "Bearer ")))))

(defn check-consented-scopes
  "Checks if every scope is mentioned in the 'scope' attribute of the token info."
  [tokeninfo scopes]
  (let [consented-scopes (set (get tokeninfo "scope"))]
    (every? consented-scopes scopes)))

(defn check-corresponding-attributes
  "Checks if every scope has a truethy attribute in the token info of the same name."
  [tokeninfo scopes]
  (let [scope-as-attribute-true? (fn [scope]
                                   (get tokeninfo scope))]
    (every? scope-as-attribute-true? scopes)))

(defcommand
  fetch-tokeninfo
  [tokeninfo-url access-token]
  (let [response (client/get tokeninfo-url
                             {:query-params     {:access_token access-token}
                              :throw-exceptions false
                              :as               :json-string-keys})]
    (if (client/server-error? response)
      (throw (IllegalStateException. (str "tokeninfo endpoint returned status code: " (:status response))))
      response)))

(defn resolve-access-token-real
  "Checks with a tokeninfo endpoint for the token's validity and returns the session information if valid."
  [tokeninfo-url access-token]
  (let [response (fetch-tokeninfo tokeninfo-url access-token)
        body (:body response)]
    (when (client/success? response) body)))

(def resolve-access-token
  "Cache token info for 2 minutes"
  (memo/fifo resolve-access-token-real (cache/ttl-cache-factory {} :ttl 120000) :fifo/threshold 100))

(defn oauth-2.0
  "Checks OAuth 2.0 tokens.
   * config-fn takes one parameter for getting configuration values. Configuration values:
   ** :tokeninfo-url
   * check-scopes-fn takes tokeninfo and scopes data and returns if token is valid"
  [get-config-fn check-scopes-fn & {:keys [resolver-fn]
                                    :or   {resolver-fn resolve-access-token}}]
  (fn [request definition requirements]
    ; get access token from request
    (if-let [access-token (extract-access-token request)]
      (let [tokeninfo-url (get-config-fn :tokeninfo-url)]
        (if tokeninfo-url
          ; check access token
          (if-let [tokeninfo (resolver-fn tokeninfo-url access-token)]
            ; check scopes
            (if (check-scopes-fn tokeninfo requirements)
              (assoc request :tokeninfo tokeninfo)
              (forbidden "scopes not granted"))
            (unauthorized "invalid access token"))
          (api/error 503 "token info misconfigured")))
      (unauthorized-without-logging "no access token given"))))