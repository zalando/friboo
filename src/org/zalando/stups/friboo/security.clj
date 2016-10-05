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

(ns org.zalando.stups.friboo.security
  (:require [org.zalando.stups.friboo.log :as log]
            [clj-http.client :as client]
            [clojure.core.memoize :as memo]
            [clojure.core.cache :as cache]
            [io.sarnowski.swagger1st.util.api :as api]
            [com.netflix.hystrix.core :refer [defcommand]]))

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
  "Returns a swagger1st security handler that allows everything."
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
    (every? #(contains? consented-scopes %) scopes)))

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
  "Checks with a tokeninfo endpoint for the token's validity and returns the session information if valid.
  Otherwise returns nil."
  [tokeninfo-url access-token]
  (let [response (fetch-tokeninfo tokeninfo-url access-token)]
    (when (client/success? response)
      (:body response))))

(def resolve-access-token
  "Cache token info for 2 minutes"
  (memo/fifo resolve-access-token-real (cache/ttl-cache-factory {} :ttl 120000) :fifo/threshold 100))

(defn oauth2
  "Returns a swagger1st security handler that checks OAuth 2.0 tokens.
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

(defn make-oauth2-security-handler [tokeninfo-url]
  (if tokeninfo-url
    (do
      (log/info "Checking access tokens against %s." tokeninfo-url)
      (oauth2 {:tokeninfo-url tokeninfo-url}
              check-corresponding-attributes
              :resolver-fn resolve-access-token))
    (do
      (log/warn "No token info URL configured; NOT ENFORCING SECURITY!")
      (allow-all))))

(def allow-all-handlers {"oauth2" (allow-all)
                         "basic"  (allow-all)
                         "apiKey" (allow-all)})
