(ns org.zalando.stups.friboo.user
  (:require [clj-http.client :as http]
            [org.zalando.stups.friboo.ring :as r]
            [io.sarnowski.swagger1st.util.api :as api]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [org.zalando.stups.friboo.log :as log]
            [com.netflix.hystrix.core :as hystrix])
  (:import (com.netflix.hystrix.exception HystrixRuntimeException)))

(hystrix/defcommand
  get-teams
  [team-service-url access-token user-id]
  (:body (http/get (r/conpath team-service-url "/user/" user-id)
                   {:oauth-token access-token
                    :as          :json})))

(defn require-teams
  "Returns a set of teams, a user is part of or throws an exception if user is in no team."
  ([request]
   (require-teams (:tokeninfo request) (require-config (:configuration request) :team-service-url)))
  ([tokeninfo team-service-url]
   (require-teams (get tokeninfo "uid") (get tokeninfo "access_token") team-service-url))
  ([user-id token team-service-url]
   (when-not user-id
     (log/warn "ACCESS DENIED (unauthenticated) because token does not contain user information.")
     (api/throw-error 403 "no user information available"))
   (try
     (let [teams (get-teams team-service-url token user-id)]
       (if (empty? teams)
         (do
           (log/warn "ACCESS DENIED (unauthorized) because user is not any team.")
           (api/throw-error 403 "user has no teams"
                            {:user user-id}))
         (into #{} (map :id teams))))
     (catch HystrixRuntimeException e
       (let [cause (-> e .getCause .toString)]
         (log/warn "Team service at %s unavailable. Cause: %s" team-service-url cause)
         (api/throw-error 503 "team service unavailable" {:team_service_url team-service-url :cause cause}))))))

(defn require-team
  "Throws an exception if user is not in the given team, else returns nil."
  ([team request]
   (require-team team (:tokeninfo request) (require-config (:configuration request) :team-service-url)))
  ([team tokeninfo team-service-url]
   (require-team team (get tokeninfo "uid") (get tokeninfo "access_token") team-service-url))
  ([team user-id token team-service-url]
   (let [in-team? (require-teams user-id token team-service-url)]
     (when-not (in-team? team)
       (log/warn "ACCESS DENIED (unauthorized) because user is not in team %s." team)
       (api/throw-error 403 (str "user not in team '" team "'")
                        {:user          user-id
                         :required-team team
                         :user-teams    in-team?})))))

(defn require-realms
  "Throws an exception if user is not in the given realms, else returns the user's realm"
  [realms {:keys [tokeninfo]}]
  (let [realm (get tokeninfo "realm")
        user-id (get tokeninfo "uid")]
    (if (contains? realms realm)
      realm
      (do
        (log/warn "ACCESS DENIED (unauthorized) because user is not in realms %s." realms)
        (api/throw-error 403 (str "user not in realms '" realms "'")
                         {:user            user-id
                          :required-realms realms
                          :user-realm      realm})))))

(defn require-internal-user
  "Makes sure the user is either a service user, or an employee.
   In the latter case the user must belong to at least one team."
  [{:keys [tokeninfo] :as request}]
  (when tokeninfo
    (let [realm (require-realms #{"employees" "services"} request)]
      (when (= realm "employees")
        (require-teams request)))))

(defn require-internal-team
  "Makes sure the user is an employee and belongs to the given team."
  [team {:keys [tokeninfo] :as request}]
  (when tokeninfo
    (require-realms #{"employees"} request)
    (require-team team request)))

(defn require-any-internal-team
  "Makes sure the user is an employee and belongs to any team."
  [{:keys [tokeninfo] :as request}]
  (when tokeninfo
    (require-realms #{"employees"} request)
    (require-teams request)))