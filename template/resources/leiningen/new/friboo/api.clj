(ns {{namespace}}.api
  (:require [org.zalando.stups.friboo.system.http :refer [def-http-component]]
            [org.zalando.stups.friboo.ring :refer :all]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [io.sarnowski.swagger1st.util.api :refer [throw-error]]
            [{{namespace}}.db :as db]
            [ring.util.response :refer :all]))

; define the API component and its dependencies
(def-http-component API "api/api.yaml" [db])

(def default-http-configuration
  {:http-port 8080})

(defmacro wrap-handler
  "Common part for every handler function, including content-type-json and nice exception handling."
  [& body]
  `(content-type-json
     (try
       ~@body
       (catch Exception e#
         (log/error e# "Unhandled exception.")
         (throw-error 500 "Internal server error" (str e#))))))

(defn get-hello
  "Says hello"
  [{:keys [name] :as params} request db]
  (wrap-handler
    (log/warn "Hello called for %s" name)
    (response {:message (str "Hello " name)})))

(defn create-or-update-greeting-template
  [{:keys [greeting_id greeting_template] :as params} request db]
  (wrap-handler
    (db/cmd-create-or-update-greeting!
      (merge greeting_template {:id greeting_id})
      {:connection db})
    {:status 204}))

(defn delete-greeting-template
  [{:keys [greeting_id] :as params} request db]
  (wrap-handler
    (db/cmd-delete-greeting! {:id greeting_id} {:connection db})
    {:status 204}))

(defn get-templated-greeting
  "Says hello"
  [{:keys [name greeting_id] :as params} request db]
  (wrap-handler
    (log/info "Hello called for %s with greeting template id %s" name greeting_id)
    (if-let [greeting-template (first (db/cmd-get-greeting {:id greeting_id} {:connection db}))]
      (response {:message (format (:g_template greeting-template) name)})
      (not-found {:message (str "Template not found with ID: " greeting_id)}))))

(comment
  (db/cmd-create-or-update-greeting! {:id "german" :template "Hallo, %s"} {:connection (:db user/system)})
  (db/get-all-greetings {} {:connection (:db user/system)})
  (db/get-greeting {:id "german"} {:connection (:db user/system)})
  )
