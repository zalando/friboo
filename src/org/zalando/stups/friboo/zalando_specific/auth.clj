(ns org.zalando.stups.friboo.zalando-specific.auth
  (:require [clj-http.client :as http]
            [org.zalando.stups.friboo.ring :as r]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [com.stuartsierra.component :refer [Lifecycle]]
            [com.netflix.hystrix.core :as hystrix :refer [defcommand]]))

(defn- log-hystrix-fallback
  [& [_ policy team]]
  (log/warn (str "auth/fetch-auth unavailable, denying access as fallback: " policy "/" team)))

(defcommand fetch-auth
  "Asks magnificent if the user for this token has access to resources of this team. Returns true or false."
  {:hystrix/fallback-fn (comp (constantly false) log-hystrix-fallback)}
  [{:as this :keys [configuration]}
   access-token
   payload]
  (if-let [magnificent-url (:magnificent-url configuration)]
    (let [policy        (:magnificent-policy configuration "relaxed-radical-agility")
          auth-response (http/get
                          (r/conpath magnificent-url "/auth")
                          {:content-type     :json
                           :oauth-token      access-token
                           :throw-exceptions false
                           :form-params      {:policy  policy
                                              :payload payload}})]
      (= 200 (:status auth-response)))
    true))

(defn get-auth
  "Convenience wrapper around fetch-auth, with logging"
  [{:as this :keys [configuration]}
   {:as tokeninfo :strs [access_token realm uid]}
   payload]
  (if-not (:magnificent-url configuration)
    ;; Don't log anything
    true
    (let [has-access? (fetch-auth this access_token payload)]
      (log/info (str "Access " (if has-access? "granted" "denied") ": %s") {:payload payload :user uid :realm realm})
      has-access?)))

(defn require-auth
  "Like get-auth, but throws an error if user has no access"
  [this
   {:as tokeninfo :strs [uid]}
   payload]
  (when-not (get-auth this tokeninfo payload)
    (api/throw-error 403 "ACCESS DENIED" {:payload payload :user uid})))

(defn start-component [{:as this :keys [configuration]}]
  (log/info "Starting Authorizer.")
  (when-not (:magnificent-url configuration)
    (log/warn "No configuration of magnificent, auth/get-auth will always return true!"))
  this)

(defn stop-component [this]
  (log/info "Stopping Authorizer.")
  this)

(defrecord Authorizer [configuration]
  Lifecycle
  (start [this]
    (start-component this))
  (stop [this]
    (stop-component this)))
