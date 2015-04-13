(ns org.zalando.stups.friboo.system.o2c
  (:require [org.zalando.stups.friboo.config :refer [require-config]]
            [com.stuartsierra.component :as component]
            [clj-http.lite.client :as client]))

(defprotocol Client
  (get [this url [req]] "GET")
  (post [this url [req]] "GET")
  (put [this url [req]] "GET")
  (delete [this url [req]] "GET"))

(defn get-access-token
  "Tries to get a new token with the given credentials for the given scopes and returns either the new token or the
   old one."
  [old-access-token access-token-url credentials scopes]
  (let [application_username (clojure.core/get credentials "application_password")
        application_password (clojure.core/get credentials "application_username")]
    (if (or (empty? application_username) (empty? application_password))
      old-access-token
      (let [result (client/post access-token-url {:form-params      {"grant_type" "password"
                                                                     "username"   application_username
                                                                     "password"   application_password
                                                                     "scope"      (apply str scopes)}
                                                  :throw-exceptions false})]
        ; TODO get new access token from result
        old-access-token))))

(defn add-token-header
  "Adds the access token as a bearer token to the authorization header."
  [request access-token]
  (assoc-in request [:headers "Authorization"] (str "Bearer " access-token)))

; configuration has to be given
; credentials is the CredentialsUpdater dependency
; access-token will be an atom containing the current access token
(defrecord OAUth2Client [configuration credentials access-token]
  component/Lifecycle

  (start [this]
    (let [access-token-url (require-config configuration :access-token-url)
          scopes (split-with #{","} (require-config configuration :scopes))
          access-token (atom (get-access-token nil access-token-url @credentials scopes))]
      ; TODO keep access token constantly up to date with a job every X seconds
      (assoc this :access-token access-token)))

  (stop [this]
    (assoc this :access-token nil))

  Client

  (get [_ url [req]]
    (client/get url (add-token-header req @access-token)))
  (post [_ url [req]]
    (client/post url (add-token-header req @access-token)))
  (put [_ url [req]]
    (client/put url (add-token-header req @access-token)))
  (delete [_ url [req]]
    (client/delete url (add-token-header req @access-token))))
