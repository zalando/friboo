(ns org.zalando.stups.friboo.zalando-specific.auth
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
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
  [{:keys [configuration]} team token]
  (if-let [magnificent-url (:magnificent-url configuration)]
    (let [policy        (:magnificent-policy configuration "relaxed-radical-agility")
          auth-response (http/get
                          (r/conpath magnificent-url "/auth")
                          {:content-type     :json
                           :oauth-token      token
                           :throw-exceptions false
                           :body             (json/encode {:policy  policy
                                                           :payload {:team team}})})]
      (= 200 (:status auth-response)))
    true))

(defn get-auth
  "Convenience wrapper around fetch-auth, with logging"
  [{:as this :keys [configuration]} request team]
  (if-not (:magnificent-url configuration)
    true                                                    ; Don't log anything
    (let [token       (get-in request [:tokeninfo "access_token"])
          realm       (get-in request [:tokeninfo "realm"])
          user        (get-in request [:tokeninfo "uid"] "unknown user")
          has-access? (fetch-auth this team token)]
      (log/info (str "Access " (if has-access? "granted" "denied") ": %s") {:team team :user user :realm realm})
      has-access?)))

(defn require-auth
  "Like get-auth, but throws an error if user has no access"
  [this request team]
  (let [has-access? (get-auth this request team)
        user        (get-in request [:tokeninfo "uid"] "unknown user")]
    (when-not has-access?
      (api/throw-error 403 "ACCESS DENIED" {:team team :user user}))))

(defn start-component [{:as this :keys [configuration]}]
  (log/info "Starting Authorizer.")
  (when-not (:magnificent-url configuration)
    (log/warn "No configuration of magnificent, auth/get-auth will always return true!"))
  this)

(defn stop-component [this]
  (log/info "Stopping Authorizer.")
  this)

(defrecord Authorizer [;; parameters (filled in by make-authorizer on creation)
                       configuration
                       ;; dependencies (filled in by the component library before starting)
                       ;; runtime vals (filled in by start-component)
                       ]
  Lifecycle
  (start [this]
    (start-component this))
  (stop [this]
    (stop-component this)))
