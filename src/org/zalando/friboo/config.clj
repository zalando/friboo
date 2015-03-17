(ns org.zalando.friboo.config
  (:require [environ.core :refer [env]]
            [clojure.tools.logging :as log]
            [clojure.string :refer [replace-first]]
            [amazonica.aws.kms :as kms]
            [clojure.data.codec.base64 :as b64]))

(def aws-kms-prefix "aws:kms:")

(defn- strip [namespace k]
  (keyword (replace-first (name k) (str (name namespace) "-") "")))

(defn- namespaced [config namespace]
  (if (contains? config namespace)
    (config namespace)
    (into {} (map (fn [[k v]] [(strip (name namespace) k) v])
                  (filter (fn [[k v]]
                            (.startsWith (name k) (str (name namespace) "")))
                          config)))))

(defn- parse-namespaces [config namespaces]
  (let [namespaced-configs (into {} (map (juxt identity (partial namespaced config)) namespaces))]
    (doseq [[namespace namespaced-config] namespaced-configs]
      (log/debug "Destructured" namespace "into" namespaced-config))
    namespaced-configs))

(defn- get-kms-ciphertext-blob [s]
  "Convert config string to ByteBuffer"
  (-> s
      (clojure.string/replace-first aws-kms-prefix "")
      .getBytes
      b64/decode
      java.nio.ByteBuffer/wrap))

(defn- decrypt-value-with-aws-kms [value aws-region-id]
  "Use AWS Key Management Service to decrypt the given string (must be encoded as Base64)"
  (->> value
       get-kms-ciphertext-blob
       (#(kms/decrypt {:endpoint aws-region-id} :ciphertext-blob %))
       :plaintext
       .array
       (map char)
       (apply str)))

(defn- decrypt-value [key value aws-region-id]
  "Decrypt a single value, returns original value if it's not encrypted"
  (if (and (string? value) (.startsWith value aws-kms-prefix))
    (do
      (log/info "decrypting configuration" key)
      (decrypt-value-with-aws-kms value aws-region-id))
    value))

(defn- decrypt [config]
  "Decrypt all values in a config map"
  (into {} (for [[k v] config]
             [k (decrypt-value k v (:aws-region-id config))])))

(defn load-configuration
  "Loads configuration options from various places."
  [default-configuration & namespaces]
  (parse-namespaces
    (decrypt
      (merge default-configuration env))
    (conj namespaces :system :http :db)))
