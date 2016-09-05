(ns org.zalando.stups.friboo.system.digest
  (:import (java.security MessageDigest)))

(defn digest
  ([input]
   (digest input "SHA-256"))
  ([input algorithm]
   {:pre [(string? input)
          (not (clojure.string/blank? input))
          (#{"SHA-256" "SHA-1" "MD5"} algorithm)]}
   (let [input-bytes (.getBytes input "UTF-8")
         md          (doto
                       (MessageDigest/getInstance algorithm)
                       (.update input-bytes))
         hash        (.digest md)
         result      (apply str (map #(format "%02x" (bit-and % 0xff)) hash))]
     result)))
