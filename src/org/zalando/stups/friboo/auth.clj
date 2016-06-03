(ns org.zalando.stups.friboo.auth
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [org.zalando.stups.friboo.ring :as r]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [com.netflix.hystrix.core :as hystrix :refer [defcommand]]))

(defn- log-hystrix-fallback
  [& [_ policy team]]
  (log/warn (str "auth/fetch-auth unavailable, denying access as fallback: " policy "/" team)))

(defcommand fetch-auth
  "Asks magnificent if the user for this token has access to resources of this team. Returns true or false."
  {:hystrix/fallback-fn (comp
                          (constantly false)
                          log-hystrix-fallback)}
  [magnificent-url policy team token]
  (let [auth-response (http/get
                        (r/conpath magnificent-url "/auth")
                        {:content-type     :json
                         :oauth-token      token
                         :throw-exceptions false
                         :body             (json/encode {:policy  policy
                                                         :payload {:team team}})})]
    (= 200 (:status auth-response))))

(defn get-auth
  "Convenience wrapper around fetch-auth, with logging"
  [request team]
  (let [magnificent-url (get-in request [:configuration :magnificent-url])]
    (if-not magnificent-url
      true
      (let [policy      (get-in request [:configuration :magnificent-policy] "relaxed-radical-agility")
            token       (get-in request [:tokeninfo "access_token"])
            realm       (get-in request [:tokeninfo "realm"])
            user        (get-in request [:tokeninfo "uid"] "unknown user")
            has-access? (fetch-auth magnificent-url policy team token)]
        (log/info (str "Access " (if has-access? "granted" "denied") ": %s") {:team team :user user :realm realm})
        has-access?))))

(defn require-auth
  "Like get-auth, but throws an error if user has no access"
  [request team]
  (let [has-access? (get-auth request team)
        user        (get-in request [:tokeninfo "uid"] "unknown user")]
    (when-not has-access?
      (api/throw-error 403 "ACCESS DENIED" {:team team :user user}))))
