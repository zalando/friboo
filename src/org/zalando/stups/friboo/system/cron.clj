(ns org.zalando.stups.friboo.system.cron
  (:require [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [overtone.at-at :as at])
  (:import (overtone.at_at RecurringJob ScheduledJob)
           (org.zalando.stups.txdemarcator Transactions)
           (com.newrelic.api.agent Trace NewRelic)))

;; got from overtaone/at-at

(defn format-date
  "Format date object as a string such as: 15:23:35s"
  [date]
  (.format (java.text.SimpleDateFormat. "EEE hh':'mm':'ss's'") date))

(defn format-start-time
  [date]
  (if (< date (at/now))
    ""
    (str ", starts at: " (format-date date))))

(defn recurring-job-string
  [job]
  (str "[" (:id job) "] "
       "recurring job created: " (format-date (:created-at job))
       (format-start-time (+ (:created-at job) (:initial-delay job)))
       ", period: " (:ms-period job) "ms"
       ",  desc: \"" (:desc job) "\""))

(defn scheduled-job-string
  [job]
  (str "[" (:id job) "]"
       " scheduled job created: " (format-date (:created-at job))
       (format-start-time (+ (:created-at job) (:initial-delay job)))
       ", desc: \"" (:desc job) "\""))

(defn job-string
  [job]
  (cond
    (= RecurringJob (type job)) (recurring-job-string job)
    (= ScheduledJob (type job)) (scheduled-job-string job)))

(definterface NewRelicTraceable
  (callWithTrace [transaction-name callback]))

(deftype NewRelicTracer []
  NewRelicTraceable
  ; create a method with the @Trace annotation
  (^{Trace {:dispatcher true}}
  callWithTrace [_ transaction-name callback]
    ; set up transaction name
    (NewRelic/setTransactionName nil transaction-name)
    (callback)))

(def newrelic-tracer (NewRelicTracer.))

(defmacro job
  "Produces a non-argument function that also marks the execution for transaction naming."
  [f & args]
  (let [{:keys [ns name]} (-> f resolve meta)
        tx-name# (str ns "/" name)]
    `(fn []
       (try
         (.callWithTrace newrelic-tracer ~tx-name#
                         (fn []
                           (Transactions/runAsBackgroundTransaction ~tx-name# #(~f ~@args))))
         (catch Exception e#
           (log/error e# "No job catch-all defined for job %s; bubbled up exception; no further executions will occur!"
                      ~tx-name#)
           (throw e#))))))

;; component

(defn create-pool [configuration]
  (apply at/mk-pool (flatten (seq configuration))))

(defmacro def-cron-component
  "Defines a new cron job component."
  [name dependencies & jobs]
  `(defrecord ~name [~(symbol "configuration") ~(symbol "pool") ~@dependencies]
     component/Lifecycle

     (start [this#]
       (let [~(symbol "pool") (create-pool ~(symbol "configuration"))]

         ~@jobs

         ; log scheduled jobs
         (let [jobs# (at/scheduled-jobs ~(symbol "pool"))]
           (if (empty? jobs#)
             (log/warn "No jobs are currently scheduled.")
             (dorun
              (map #(log/info (job-string %)) jobs#))))

         (assoc this# :pool ~(symbol "pool"))))

     (stop [this#]
       (when-let [pool# (:pool this#)]
         (at/stop-and-reset-pool! pool# :strategy :kill))
       (dissoc this# :pool))))
