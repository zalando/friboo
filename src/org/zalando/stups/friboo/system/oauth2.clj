; Copyright © 2016 Zalando SE
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.zalando.stups.friboo.system.oauth2
  (:require [org.zalando.stups.friboo.config :refer [require-config]]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [clj-http.client :as client]
            [clojure.core.cache :as cache]
            [clojure.core.memoize :as memo]
            [org.zalando.stups.friboo.log :as log]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import (org.zalando.stups.tokens Tokens ClientCredentialsProvider ClientCredentials UserCredentialsProvider UserCredentials CredentialsUnavailableException)
           (java.net URI)))

(defn make-client-credentials-provider [file]
  (reify ClientCredentialsProvider
    (get [_]
      (let [{:strs [client_id client_secret]} (json/parse-string (slurp file))]
        (reify ClientCredentials
          (getId [_] client_id)
          (getSecret [_] client_secret))))))

(defn make-user-credentials-provider [file]
  (reify UserCredentialsProvider
    (get [_]
      (let [{:strs [application_username application_password]} (json/parse-string (slurp file))]
        (reify UserCredentials
          (getUsername [_] application_username)
          (getPassword [_] application_password))))))

; configuration has to be given, contains links to IAM solution and timings
; tokens has to be given, contains map of tokenids to list of required scopes
; token-storage will be an atom containing the current access token
(defrecord OAuth2TokenRefresher [configuration tokens token-storage]
  component/Lifecycle

  (start [this]
    (let [access-token-url (require-config configuration :access-token-url)
          credentials-dir  (require-config configuration :credentials-dir)
          ;; Provide custom implementations to prevent the library from using System.getenv("CREDENTIALS_DIR")
          token-builder    (-> (Tokens/createAccessTokensWithUri (URI. access-token-url))
                               (.usingClientCredentialsProvider
                                 (make-client-credentials-provider (io/file credentials-dir "client.json")))
                               (.usingUserCredentialsProvider
                                 (make-user-credentials-provider (io/file credentials-dir "user.json"))))]

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
  [token-id ^OAuth2TokenRefresher refresher]
  (let [token-storage (:token-storage refresher)]
    (.get token-storage token-id)))
