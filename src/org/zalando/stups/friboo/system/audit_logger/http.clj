(ns org.zalando.stups.friboo.system.audit-logger.http
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.ring :as ring]
            [org.zalando.stups.friboo.system.digest :as d]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.friboo.log :as log]))

(defn log
  [config event]
  (let [body       (json/encode event)
        token-name (or (:token-name config) :http-audit-logger)
        id         (d/digest body)
        url        (ring/conpath (:api-url config) id)]
    (future
      (try
        (http/put url {:body         body
                       :oauth-token  (oauth2/access-token token-name (:tokens config))
                       :content-type :json})
        (log/info "Wrote audit event with id %s" id)
        (catch Exception e
          ; log to console as fallback
          (log/error e "Could not write audit event: %s" body))))))

(defn logger-factory
  [config tokens]
  (partial log (->
                 config
                 (assoc :tokens tokens))))

(defrecord HTTP
  [configuration tokens]
  component/Lifecycle
  (start
    [this]
    (if (:log-fn this)
      (do
        (log/info "HTTP audit logger already running")
        this)
      (do
        (log/info "Starting HTTP audit logger")
        (assoc this :log-fn (logger-factory configuration tokens)))))
  (stop
    [this]
    (log/info "Shutting down HTTP audit logger")
    (dissoc this :log-fn)))
