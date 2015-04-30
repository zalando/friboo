(ns org.zalando.stups.friboo.system.oauth2
  (:require [org.zalando.stups.friboo.config :refer [require-config]]
            [com.stuartsierra.component :as component]
            [clj-http.client :as client]
            [overtone.at-at :as at]
            [org.zalando.stups.friboo.system.credentials :as c]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as string]
            [clj-time.core :as t]))

(defn refresh-token
  "Tries to get a new token with the given credentials for the given scopes and returns new token info."
  [access-token-url credentials scopes]
  (let [user-credentials (c/credentials credentials :user)
        client-credentials (c/credentials credentials :client)]
    ; do we have credentials?
    (if (or (empty? user-credentials) (empty? client-credentials))
      (throw (IllegalStateException. "not all necessary credentials are available"))

      (let [application-username (:application_username user-credentials)
            application-password (:application_password user-credentials)
            client-id (:client_id client-credentials)
            client-secret (:client_secret client-credentials)]
        (log/debug "Requesting access token for %s with client %s with scopes %s on URL %s..."
                   application-username client-id scopes access-token-url)
        (let [response (client/post access-token-url
                                    {:basic-auth  [client-id client-secret]
                                     :form-params {"grant_type" "password"
                                                   "username"   application-username
                                                   "password"   application-password
                                                   "scope"      (string/join " " scopes)}
                                     :as          :json})
              now (t/now)
              body (:body response)
              token-info (merge body
                                {:expires_in (t/plus now (t/seconds (:expires_in body)))
                                 :created_on now})]
          (log/debug "Got new token info %s." token-info)
          token-info)))))

(defn token-needs-refresh?
  "Checks if a token needs refresh."
  [token-info]
  (if (empty? token-info)
    true
    ; TODO expiration time is only 40% left
    (t/after? (t/now) (:expires_in token-info))))

(defn refresh-tokens
  "Checks for every configured token if it requires refresh and triggers if necessary."
  [access-token-url tokens credentials token-storage]
  (try
    (doseq [[token-id scopes] tokens]
      (let [token-info (get @token-storage token-id)]
        (if (token-needs-refresh? token-info)
          (do
            (log/debug "Token %s needs refresh; refreshing..." token-id)
            (try
              (let [token-info (refresh-token access-token-url credentials scopes)]
                (swap! token-storage assoc token-id token-info)
                (log/info "Access token %s refreshed." token-id))
              (catch Exception e
                ; TODO only debug message if time left > 20%, warn if time left <= 20%
                (log/warn "Could not refresh access token %s because %s." token-id (str e)))))
          ; TODO no need for refresh but check every 20% of time with the tokeninfo endpoint if really still valid
          )))
    (catch Throwable e
      (log/error e "Unexpected error during refresh of token: %s" (str e)))))

; configuration has to be given, contains links to IAM solution and timings
; tokens has to be given, contains map of tokenids to list of required scopes
; credentials is the CredentialsUpdater dependency
; pool will be the at-at pool of the scheduled job
; token-storage will be an atom containing the current access token
(defrecord OAUth2TokenRefresher [configuration tokens credentials pool token-storage]
  component/Lifecycle

  (start [this]
    (let [access-token-url (require-config configuration :access-token-url)
          token-storage (atom {})
          pool (at/mk-pool)]

      (when (empty? tokens)
        (throw (IllegalArgumentException. "no 'tokens' configuration given to token refresher")))

      ; initially try on boot
      (refresh-tokens access-token-url tokens credentials token-storage)
      ; and then every 5 sec
      (at/every 5000 #(refresh-tokens access-token-url tokens credentials token-storage) pool :initial-delay 5000)

      (merge this {:token-storage token-storage
                   :pool          pool})))

  (stop [this]
    (at/stop-and-reset-pool! pool :strategy :kill)
    (merge this {:access-tokens nil
                 :pool          nil})))

(defn access-token
  "Returns the valid access token of the given ID."
  [token-id ^OAUth2TokenRefresher refresher]
  (let [token-storage (:token-storage refresher)
        token-info (get @token-storage token-id)]
    ; check if token is available and still valid
    (if (and token-info (t/before? (t/now) (:expires_in token-info)))
      (:access_token token-info)
      (throw (IllegalStateException. (str "no valid access token " token-id " available"))))))
