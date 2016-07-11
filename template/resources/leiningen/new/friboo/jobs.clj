(ns {{namespace}}.jobs
  (:require [org.zalando.stups.friboo.system.cron :refer [def-cron-component]]
            [org.zalando.stups.friboo.log :as log]
            [overtone.at-at :refer [every]]))

(def default-jobs-configuration
  {:jobs-every-ms         15000
   :jobs-initial-delay-ms 1000})

(defn run-job [configuration]
  (log/info "Hello from background job, configuration: %s" configuration))

(def-cron-component
  Jobs []
  (let [{:keys [every-ms initial-delay-ms]} configuration]
    (every every-ms (partial run-job configuration) pool :initial-delay initial-delay-ms :desc "Hello Jobs")))
