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

(ns org.zalando.stups.friboo.config
  (:require [environ.core :as environ]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str]
            [amazonica.aws.kms :as kms]
            [clojure.data.codec.base64 :as b64]))

(defn- nil-or-empty?
  "If x is a string, returns true if nil or empty. Else returns true if x is nil"
  [x]
  (if (string? x)
    (empty? x)
    (nil? x)))

(defn require-config
  "Helper function to fail of a configuration value is missing."
  [configuration key]
  (let [value (get configuration key)]
    (if (nil-or-empty? value)
      (throw (IllegalArgumentException. (str "Configuration " key " is required but is missing.")))
      value)))

(def aws-kms-prefix "aws:kms:")

(defn- is-sensitive-key [k]
  (let [kname (name k)]
    (or (.contains kname "pass")
        (.contains kname "private")
        (.contains kname "secret"))))

(defn mask [config]
  "Mask sensitive information such as passwords"
  (into {} (for [[k v] config] [k (if (is-sensitive-key k) "MASKED" v)])))

(defn- strip [namespace k]
  (keyword (str/replace-first (name k) (str (name namespace) "-") "")))

(defn- namespaced [config namespace]
  (if (contains? config namespace)
    (config namespace)
    (into {} (map (fn [[k v]] [(strip (name namespace) k) v])
                  (filter (fn [[k v]]
                            (.startsWith (name k) (str (name namespace) "")))
                          config)))))

(defn parse-namespaces [config namespaces]
  (let [namespaced-configs (into {} (map (juxt identity (partial namespaced config)) namespaces))]
    (doseq [[namespace namespaced-config] namespaced-configs]
      (log/debug "Destructured %s into %s." namespace (mask namespaced-config)))
    namespaced-configs))

(defn- get-kms-ciphertext-blob [s]
  "Convert config string to ByteBuffer"
  (-> s
      (clojure.string/replace-first aws-kms-prefix "")
      .getBytes
      b64/decode
      java.nio.ByteBuffer/wrap))

(defn decrypt-value-with-aws-kms [value aws-region-id]
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

(defn load-configuration
  "Loads the configuration from different sources and transforms it.

  Takes default configurations, merges them in the order they are provided:
  [{:http-port 8080 :tokeninfo-url \"bar\"}, {:tokeninfo-url \"foo\"}] -> {:http-port 8080 :tokeninfo-url \"foo\"}
  Then merges the environment variables into it:
  {:http-port 8080 :tokeninfo-url \"foo\"}, {:http-port 9090} -> {:http-port 9090 :tokeninfo-url \"foo\"}
  Then optionally renames some keys, but only if the new key does not exist:
  {:http-tokeninfo-url :tokeninfo-url}, {:http-port 9090 :tokeninfo-url \"foo\"}
    -> {:http-port 9090 :tokeninfo-url \"foo\" :http-tokeninfo-url \"foo\"}
  Then filters out by provided namespace prefixes:
  [:http], {:http-port 9090 :tokeninfo-url \"foo\" :http-tokeninfo-url \"foo\"}
    -> {:http-port 9090 :http-tokeninfo-url \"foo\"}
  Then extracts namespaces:
  {:http-port 9090 :http-tokeninfo-url \"foo\"} -> {:http {:port 9090 :tokeninfo-url \"foo\""

  ;; TODO in version 2 use only one default configuration, let the caller call merge
  ([namespaces default-configurations]
   (load-configuration namespaces
                       ;; TODO in version 2 don't use default demapping, move to a separate Zalando-specific namespace
                       {:http-tokeninfo-url     :tokeninfo-url
                        :oauth2-credentials-dir :credentials-dir}
                       default-configurations))

  ([namespaces globals-mapping default-configurations]
   (let [env-with-defaults                (apply merge (conj default-configurations environ/env))
         remappings                       (into {} (for [[new-key old-key] globals-mapping
                                                         :let [old-value (get env-with-defaults old-key)]
                                                         :when old-value]
                                                     [new-key old-value]))
         env-with-remappings-and-defaults (merge remappings env-with-defaults)]
     (parse-namespaces (decrypt env-with-remappings-and-defaults) (conj namespaces :system)))))
