; Copyright © 2015 Zalando SE
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
(ns org.zalando.stups.friboo.system.audit-log
  (:require [com.stuartsierra.component :refer [Lifecycle]]
            [org.zalando.stups.friboo.log :as log]
            [cheshire.core :as json]
            [cheshire.generate :refer [add-encoder]]
            [clojure.core.incubator :refer [dissoc-in]]
            [clj-time.core :as t]
            [amazonica.aws.s3 :as s3]
            [overtone.at-at :as at]
            [clj-time.format :as tf]
            [clojure.string :as string])
  (:import (java.io ByteArrayInputStream)
           (java.util UUID)))

; #30 encode input streams like before so tests work
(add-encoder java.io.InputStream
             (fn [stream jsonGenerator]
               (.writeString jsonGenerator
                             (str "Some " (.getName (class stream)) ". Content omitted."))))

(defn running? [component]
  (:audit-logs component))

(defn add-logs
  "Adds a list of log entries to the audit logs container."
  [audit-logs logs]
  (dosync (alter audit-logs concat logs)))

(defn empty-logs
  "Returns all current audit logs and resets the container to empty."
  [audit-logs]
  (dosync
    (let [previous-logs @audit-logs]
      (ref-set audit-logs [])
      previous-logs)))

(defn is-modifying? [{:keys [request-method]}]
  (#{:post :put :patch :delete} request-method))

(defn extract-audit-log-record [request]
  (-> request
      (assoc :logged-on (t/now))
      (dissoc :swagger)
      (dissoc :configuration)
      (dissoc :body)
      (dissoc-in [:tokeninfo "open_id"])
      (dissoc-in [:tokeninfo "access_token"])
      (dissoc-in [:headers "authorization"])))

(defn collect-audit-logs
  "Adds request map to audit-logs container."
  [next-handler component]
  (if (running? component)
    (do
      (log/info "Logging all successful, modifying requests.")
      (fn [request]
        (let [{:keys [status] :as response} (next-handler request)]
          (when (and (>= status 200)
                     (< status 300)
                     (is-modifying? request))
            (let [audit-log (extract-audit-log-record request)]
              (add-logs (:audit-logs component) [audit-log])))
          response)))
    (do
      (log/info "Not logging modifying requests.")
      next-handler)))

(def default-audit-flush-millis (* 10 1000))

(def audit-logs-file-formatter
  "yyyy/MM/dd/{app-id}/{app-version}/{instance-id}/modifying-requests-hh-mm-ss.log
  e.g. 2015/06/15/kio/b2/123456/modifying-requests-08-31-52.log"
  (let [app-id (or (System/getenv "APPLICATION_ID") "unknown-app")
        app-version (or (System/getenv "APPLICATION_VERSION") "unknown-version")
        instance-id (or (System/getenv "INSTANCE_ID") (UUID/randomUUID))
        format-string (format "yyyy/MM/dd/'%s/%s/%s/modifying-requests'-HH-mm-ss'.log'" app-id app-version instance-id)]
    (tf/formatter format-string t/utc)))

(defn store-audit-logs!
  "Stores the audit logs in an S3 bucket."
  [audit-logs bucket]
  (let [previous-logs (empty-logs audit-logs)]
    (when (seq previous-logs)
      (try
        (let [file-content (string/join "\n" (map json/encode previous-logs))
              file-stream (-> file-content (.getBytes "UTF-8") (ByteArrayInputStream.))
              content-length (count file-content)
              file-name (tf/unparse audit-logs-file-formatter (t/now))]
          (s3/put-object :bucket-name bucket
                         :key file-name
                         :input-stream file-stream
                         :metadata {:content-length content-length}))
        (catch Throwable t
          (add-logs audit-logs previous-logs)
          (log/warn "Could not store audit logs because of %s." (str t)))))))

(defn schedule-audit-log-flusher!
  [bucket audit-logs {:keys [flush-millis] :or {flush-millis default-audit-flush-millis}}]
  (let [pool (at/mk-pool :cpu-count 1)]
    (at/every flush-millis #(store-audit-logs! audit-logs bucket) pool :initial-delay flush-millis)
    pool))

(defn stop-audit-log-flusher! [thread-pool audit-logs bucket]
  (dosync
    (at/stop-and-reset-pool! thread-pool :strategy :kill)
    (store-audit-logs! audit-logs bucket)))

(defrecord AuditLog [configuration]
  Lifecycle

  (start [component]
    (log/warn "DEPRECATED use of audit-log component. Please one of the explicit audit-loggers instead.")
    (if (running? component)
      ; then
      (do
        (log/info "Audit logger already running.")
        component)
      ; else
      (if-let [bucket (:bucket configuration)]
        (let [audit-logs (ref [])
              thread-pool (schedule-audit-log-flusher! bucket audit-logs configuration)]
          (log/info "Created an audit logger.")
          (merge component {:audit-logs  audit-logs
                            :thread-pool thread-pool
                            :bucket      bucket}))
        (do
          (log/info "Skip creation of audit logger. AUDIT_LOG_BUCKET not set.")
          component))))

  (stop [component]
    (if (running? component)
      ; then
      (do
        (stop-audit-log-flusher! (:thread-pool component) (:audit-logs component) (:bucket component))
        (log/info "Shut down the audit logger.")
        (merge component {:audit-logs  nil
                          :thread-pool nil
                          :bucket      nil}))
      ; else
      (do
        (log/info "Audit logger not running.")
        component))))
