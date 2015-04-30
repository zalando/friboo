; Copyright © 2015 Zalando SE
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns org.zalando.stups.friboo.log
  (:require [clojure.tools.logging :as clojure-logging]
            [clojure.data.json :as json]
            ; loads joda datetime json serialization
            [io.sarnowski.swagger1st.parser]))

(defn format-value
  "Formats dynamic information for logs."
  [value]
  (str "[" (json/write-str value :escape-slash false) "]"))

(defn format-values
  "Formats dynamic information for logs."
  [& values]
  (map format-value values))

; TODO store in log4j MDC? makes us more dependent and less flexible with actual log format
(def ^:dynamic context {})
(def ^:dynamic context-message nil)

(defmacro log-with
  "Adds the given keys to the logging context."
  [ctx & body]
  ; TODO use linked map and sorting to have nicely sorted output?
  `(binding [context (merge context ~ctx)
             context-message (format-value context)]
     ~@body))

(defmacro logf
  "Logs a message, formatted with clear distinction for dynamic values."
  [level exception message & more]
  `(when (clojure-logging/enabled? ~level)
     (let [more# (format-values ~@more)
           msg# (apply format ~message more#)
           msg# (if (nil? ~context-message)
                  msg#
                  (str ~context-message " " msg#))]
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
