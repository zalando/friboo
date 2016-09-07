(ns org.zalando.stups.friboo.system.audit-logger.s3
  (:require [amazonica.aws.s3 :as s3]
            [com.stuartsierra.component :as component]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.format :as time-format]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.system.digest :as d]
            [org.zalando.stups.friboo.ring :as ring])
  (:import (java.io ByteArrayInputStream)))

(defn log
  [config event]
  (let [body           (json/encode event)
        id             (d/digest body)
        bucket         (:bucket config)
        content-length (count body)
        key            (time-format/unparse (:formatter config) (time/now))
        input-stream   (new ByteArrayInputStream (.getBytes body "UTF-8"))]
    (future
      (try
        (s3/put-object
          :bucket-name bucket
          :key (ring/conpath key id)
          :metadata {:content-length content-length
                     :content-type   "application/json"}
          :input-stream input-stream)
        (log/info "Wrote audit event with id %s" id)
        (catch Exception e
          (log/error e "Could not write audit event: %s" body))))))

(defn logger-factory
  [config]
  (let [bucket        (:s3-bucket config)
        format-string (or (:s3-bucket-key config) "yyyy/MM/dd/")
        formatter     (time-format/formatter format-string time/utc)]
    (partial log {:bucket    bucket
                  :formatter formatter})))

(defrecord S3
  [configuration]
  component/Lifecycle
  (start
    [this]
    (if (:log-fn this)
      (do
        (log/info "S3 audit logger already running")
        this)
      (do
        (log/info "Starting S3 audit logger")
        (assoc this :log-fn (logger-factory configuration)))))
  (stop
    [this]
    (dissoc this :log-fn)))
