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

(ns org.zalando.stups.friboo.zalando-specific.system.http
  (:require [clojure.data.codec.base64 :as b64]
            [io.clj.logging :refer [with-logging-context]]
            [io.sarnowski.swagger1st.core :as s1st]
            [org.zalando.stups.friboo.system.new-http :as http]
            [org.zalando.stups.friboo.system.metrics :as metrics]
            [org.zalando.stups.friboo.system.audit-log :as audit-log]
            [io.sarnowski.swagger1st.util.newrelic :as newrelic]
            [org.zalando.stups.friboo.security :as security]))

(defn- parse-basic-auth
  "Parse HTTP Basic Authorization header"
  [authorization]
  (-> authorization
      (clojure.string/replace-first "Basic " "")
      .getBytes
      b64/decode
      String.
      (clojure.string/split #":" 2)
      (#(zipmap [:username :password] %))))

(defn map-authorization-header
  "Map 'Token' and 'Basic' Authorization values to standard Bearer OAuth2 auth."
  [authorization]
  (when authorization
    (condp #(.startsWith %2 %1) authorization
      "Token " (.replaceFirst authorization "Token " "Bearer ")
      "Basic " (let [basic-auth (parse-basic-auth authorization)]
                 (if (= (:username basic-auth) "oauth2")
                   (str "Bearer " (:password basic-auth))
                   ; do not touch Basic auth headers if username is not "oauth2"
                   authorization))
      authorization)))

(defn map-alternate-auth-header
  "Map alternate Authorization headers to standard OAuth2 'Bearer' auth"
  [handler]
  (fn [request]
    (let [authorization     (get-in request [:headers "authorization"])
          new-authorization (map-authorization-header authorization)]
      (if new-authorization
        (handler (assoc-in request [:headers "authorization"] new-authorization))
        (handler request)))))

(defn metrics-middleware [context]
  (if-let [metrics (-> context :component :metrics)]
    (s1st/ring context metrics/collect-swagger1st-zmon-metrics metrics)
    context))

(defn audit-logger-middleware [context]
  (if-let [audit-logger (-> context :component :audit-logger)]
    (s1st/ring context audit-log/collect-audit-logs audit-logger)
    context))

(defn make-zalando-http [api-resource configuration tokeninfo-url]
  (let [middlewares (-> http/default-middlewares
                        (update :before-discoverer conj #(s1st/ring % map-alternate-auth-header))
                        (update :before-parser conj metrics-middleware)
                        (update :before-parser conj newrelic/tracer)
                        (update :before-executor conj audit-logger-middleware)
                        (update :before-mapper conj #(s1st/ring % ring.middleware.resource/wrap-resource "public")))]
    (http/map->Http
      {:api-resource      api-resource
       :configuration     configuration
       :security-handlers {"oauth2" (security/make-oauth2-security-handler tokeninfo-url)}
       :middlewares       middlewares})))
