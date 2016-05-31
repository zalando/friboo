(ns org.zalando.stups.friboo.auth
  (:require [clj-http.client :as http]
            [org.zalando.stups.friboo.ring :as r]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.friboo.log :as log]
            [io.sarnowski.swagger1st.util.api :as api]
            [com.netflix.hystrix.core :as hystrix :refer [defcommand]]))

(defcommand fetch-auth
  "Asks magnificent if the user for this token has access to resources of this team. Returns true or false."
  {:hystrix/fallback-fn (constantly false)}
  [magnificent-url policy team token]
  (let [auth-response (http/get
                        (r/conpath magnificent-url "/auth")
                        {:content-type     :json
                         :oauth-token      token
                         :throw-exceptions false
                         :body             {:policy  policy
                                            :payload {:team team}}})]
    (= 200 (:status auth-response))))

(defn get-auth
  "Convenience wrapper around fetch-auth, with logging"
  [request & [team]]
  (let [magnificent-url (require-config (:configuration request) :magnificent-url)
        policy          (require-config (:configuration request) :magnificent-policy)
        token           (get-in request [:tokeninfo "access_token"])
        user            (get-in request [:tokeninfo "uid"] "unknown user")
        has-access?     (fetch-auth magnificent-url policy team token)]
    (log/info (str "Access to team" team (if has-access? "granted" "denied") "to" user))
    has-access?))

(defn require-auth
  "Like get-auth, but throws an error if user has no access"
  [request team]
  (let [has-access? (get-auth request team)
        user        (get-in request [:tokeninfo "uid"] "unknown user")]
    (when-not has-access?
      (api/throw-error 403 (str "Access to team" team "not granted to user" user)))))
