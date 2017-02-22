(ns {{namespace}}.api
  (:require [org.zalando.stups.friboo.ring :refer :all]
            [taoensso.timbre :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [com.stuartsierra.component :as component]
            [ring.util.response :as r]))

(defrecord Controller [configuration]
  component/Lifecycle
  (start [this]
    (log/info "Starting API Controller")
    this)
  (stop [this]
    (log/info "Stopping API Controller")
    this))

(defn get-hello
  "Says hello"
  [{:as this :keys [configuration]} {:as params :keys [name]} request]
  (log/debugf "API configuration: %s" configuration)
  (log/infof "Hello called for %s" name)
  (r/response {:message (str "Hello " name) :details {:X-friboo (require-config configuration :example-param)}}))
