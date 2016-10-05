(ns org.zalando.stups.friboo.config-decrypt
  (:require [org.zalando.stups.friboo.log :as log]
            [amazonica.aws.kms :as kms]
            [clojure.data.codec.base64 :as b64])
  (:import (java.nio ByteBuffer)))

(def aws-kms-prefix "aws:kms:")

(defn- get-kms-ciphertext-blob [s]
  "Convert config string to ByteBuffer"
  (-> s
      (clojure.string/replace-first aws-kms-prefix "")
      .getBytes
      b64/decode
      ByteBuffer/wrap))

(defn decrypt-value-with-aws-kms [value aws-region-id]
  "Use AWS Key Management Service to decrypt the given string (must be encoded as Base64)"
  (->> value
       get-kms-ciphertext-blob
       (#(kms/decrypt {:endpoint aws-region-id} {:ciphertext-blob %}))
       :plaintext
       .array
       (map char)
       (apply str)))

(defn- decrypt-value [key value aws-region-id]
  "Decrypt a single value, returns original value if it's not encrypted"
  (if (and (string? value) (.startsWith value aws-kms-prefix))
    (do
      (log/info "Decrypting configuration %s." key)
      (decrypt-value-with-aws-kms value aws-region-id))
    value))

(defn- to-real-boolean
  "Maps a boolean string to boolean or returns the string."
  [value]
  (if (string? value)
    (case value
      "true" true
      "false" false
      value)
    value))

(defn- to-real-number
  "Maps a number string to long or returns the string."
  [value]
  (if (and (string? value) (not (nil? (re-matches #"^[0-9]+$" value))))
    (Long/parseLong value)
    value))

(defn decrypt [config]
  "Decrypt all values in a config map"
  (into {} (for [[k v] config]
             [k (-> (decrypt-value k v (:aws-region-id config))
                    (to-real-boolean)
                    (to-real-number))])))

(defn decrypt-config
  "Decrypts 2-level config map."
  [config-map]
  (into {} (for [[config-ns subconfig] config-map]
             [config-ns (decrypt subconfig)])))
