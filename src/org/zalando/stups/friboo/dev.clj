(ns org.zalando.stups.friboo.dev
  (:require [clojure.edn :as edn])
  (:import (java.net ServerSocket)
           (org.apache.logging.log4j LogManager)))

(defn slurp-if-exists [file]
  (when (.exists (clojure.java.io/as-file file))
    (slurp file)))

(defn load-dev-config [file]
  (edn/read-string (slurp-if-exists file)))

(defn get-free-port []
  (let [sock (ServerSocket. 0)]
    (try
      (.getLocalPort sock)
      (finally
        (.close sock)))))

(defn reload-log4j2-config []
  (.reconfigure (LogManager/getContext false)))
