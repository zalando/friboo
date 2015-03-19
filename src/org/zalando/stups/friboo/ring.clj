(ns org.zalando.stups.friboo.ring
  (:require [ring.util.response :refer :all]))

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
