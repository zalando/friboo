(ns org.zalando.stups.friboo.system.digest
  (:require [digest :as d]))

(defn digest
  ([input]
   {:pre [(string? input)
          (not (clojure.string/blank? input))]}
   (d/sha-256 input)))
