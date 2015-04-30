(ns org.zalando.stups.friboo.system.credentials
  (:require [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.log :as log]
            [org.zalando.stups.friboo.config :refer [require-config]]
            [clojure.java.io :as io]
            [clojure-watch.core :refer [start-watch]]
            [clojure.data.json :as json])
  (:import (java.io File PrintWriter)))

; provide json serialization for logging of java.io.File

(defn- serialize-file
  "Serializes a java.io.File to JSON."
  [^File file #^PrintWriter out]
  (.print out (json/write-str (str file) :escape-slash false)))

(extend File json/JSONWriter
  {:-write serialize-file})


(defn- check-file
  "Checks a file if it has new credentials and updates the credentials atom."
  [type file credentials]
  (try
    (let [data (-> file slurp (json/read-str :key-fn keyword))]
      (when (and (not-empty data) (not= data (get @credentials type)))
        (swap! credentials assoc type data)
        (log/info "New credentials for %s in %s found." type file)))
    (catch Exception e
      (log/warn "Cannot read %s credentials file %s because %s." type file (.getMessage e)))))

; configuration map has to contain :dir key
; credentials key will be set during initilization and holds atom with credentials
(defrecord CredentialUpdater [configuration credentials]
  component/Lifecycle

  (start [this]
    (if-let [dir (:dir configuration)]
      (let [credentials (atom {})
            dir (.getAbsolutePath (io/file dir))]
        (doseq [[type file] {:user   "user.json"
                             :client "client.json"}]
          (swap! credentials assoc type nil)
          (let [source (io/file dir file)]
            (log/info "Watching %s for %s credentials." source type)
            (start-watch [{:path        (.getParent source)
                           :event-types [:create :modify]
                           :callback    (fn [_ filename]
                                          (when (= filename (str source))
                                            (check-file type source credentials)))}])
            (check-file type source credentials)))
        (assoc this :credentials credentials))
      (log/warn "No credentials directory given, will not watch for any credentials.")))

  (stop [this]
    ; unfortunatly, start-watch does not provide stop-watch
    ; no problem in production and only marginal during development
    (log/warn "Cannot stop watching credentials file; leaking memory and background thread.")
    (assoc this :credentials nil)))


(defn credentials
  "Returns credentials struct from an updater."
  [^CredentialUpdater updater type]
  (let [credentials (get updater :credentials)]
    (get @credentials type)))
