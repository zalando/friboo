(ns org.zalando.stups.friboo.system.credentials
  (:require [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [clojure.java.io :as io]
            [clojure-watch.core :refer [start-watch]]
            [clojure.data.json :as json])
  (:import (java.io File PrintWriter)))

; provide json serialization
(defn- serialize-file
  "Serializes a sql timestamp to json."
  [^File file #^PrintWriter out]
  (.print out (json/write-str (str file))))

; add json capability to java.sql.Timestamp
(extend File json/JSONWriter
  {:-write serialize-file})


(defn- check-file
  "Checks a file if it has new credentials and updates the credentials atom."
  [file credentials]
  (try
    (let [content (slurp file)
          data (json/read-str content)]
      (when (and data (not (empty? data)) (not (= data @credentials))
        (reset! credentials data)
        (log/info "New credentials found for %s." (select-keys data ["application_username" "client_id"])))))
    (catch Exception e
      (log/warn "Cannot read credentials file %s because %s." file (str e)))))

; configuration map has to contain :file key
; credentials key will be set during initilization and holds atom with credentials
(defrecord CredentialUpdater [configuration credentials]
  component/Lifecycle

  (start [this]
    (let [^File file (io/file (require-config configuration :file))
          credentials (atom nil)]
      (log/info "Watching %s for credentials." file)
      (check-file file credentials)
      (start-watch [{:path        (.getParent file)
                     :event-types [:create :modify]
                     :callback    (fn [_ filename]
                                    (when (= filename (str file))
                                      (check-file file credentials)))}])
      (assoc this :credentials credentials)))

  (stop [this]
    ; unfortunatly, start-watch does not provide stop-watch
    ; no problem in production and only marginal during development
    (log/warn "Cannot stop watching credentials file; leaking memory and background thread.")
    (assoc this :credentials nil)))
