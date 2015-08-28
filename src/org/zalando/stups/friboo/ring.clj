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

(ns org.zalando.stups.friboo.ring
  (:require [ring.util.response :refer :all]
            [clojure.string :as s]))

;; some convinience helpers

(defn content-type-json
  "Sets the content-type of the response to 'application/json'."
  [response]
  (content-type response "application/json"))

(defn single-response
  "Returns 404 if results is empty or the first result, ignoring all others."
  [results]
  (if (empty? results)
    (not-found {})
    (response (first results))))

(defmacro xor
  [a b]
  `(or (and ~a (not ~b))
       (and (not ~a) ~b)))

(defn conpath
  "Concatenates path elements to an URL."
  [url & path]
  (let [[x & xs] path]
    (let [x (str x)
          first-with-slash (.endsWith url "/")
            last-with-slash  (.startsWith x "/")
            url (if (xor first-with-slash
                         last-with-slash)
                  ; concat if exactly one of them has a /
                  (str url x)
                  (if (and first-with-slash
                           last-with-slash)
                    ; if both have a /, remove one
                    (str url (s/replace-first x #"/" ""))
                    ; if none have a /, add one
                    (str url "/" x)))]
      (if xs
        (recur url xs)
        url))))
