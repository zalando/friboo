(ns {{namespace}}.controller
  (:require
    [org.zalando.stups.friboo.ring :refer :all]
    [org.zalando.stups.friboo.log :as log]
    [com.stuartsierra.component :as component]
    [org.zalando.stups.friboo.config :refer [require-config]]
    [ring.util.response :refer :all]))

(defrecord Controller [configuration]
  component/Lifecycle
  (start [this]
    (log/info "Starting controller")
    this)
  (stop [this]
    (log/info "Stopping controller")
    this))

(defn get-hello
  "Says hello"
  [{:as this :keys [configuration]} {:as params :keys [name]} request]
  (log/debug "Controller configuration: %s" configuration)
  (log/info "Hello called for %s" name)
  (response {:message (str "Hello " name) :details {:X-friboo (require-config configuration :example-param)}}))
