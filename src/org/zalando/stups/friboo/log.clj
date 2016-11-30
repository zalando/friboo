(ns org.zalando.stups.friboo.log
  (:require [clojure.tools.logging :as clojure-logging]
            [cheshire.core :as json]
            ; loads joda datetime json serialization
            [io.sarnowski.swagger1st.parser]))

(defn format-value
  "Formats dynamic information for logs."
  [value]
  (str "[" (json/encode value) "]"))

(defn format-values
  "Formats dynamic information for logs."
  [& values]
  (map format-value values))

(defmacro logf
  "Logs a message, formatted with clear distinction for dynamic values."
  [level exception message & more]
  `(when (clojure-logging/enabled? ~level)
     (let [more# (format-values ~@more)
           msg# (apply format ~message more#)]
       (clojure-logging/log ~level ~exception msg#))))

(defmacro trace [message & args]
  `(logf :trace nil ~message ~@args))

(defmacro debug [message & args]
  `(logf :debug nil ~message ~@args))

(defmacro info [message & args]
  `(logf :info nil ~message ~@args))

(defmacro warn [message & args]
  `(logf :warn nil ~message ~@args))

(defmacro error [exception message & args]
  `(logf :error ~exception ~message ~@args))

; TODO for now audit goes to info as well
(defmacro audit [message & args]
  `(logf :info nil ~message ~@args))
