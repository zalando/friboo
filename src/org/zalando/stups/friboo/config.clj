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
            [org.zalando.stups.friboo.config-decrypt :as decrypt]))

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
                            (.startsWith (name k) (str (name namespace) "-")))
                          config)))))

(defn parse-namespaces [config namespaces]
  (let [namespaced-configs (into {} (map (juxt identity (partial namespaced config)) namespaces))]
    (doseq [[namespace namespaced-config] namespaced-configs]
      (log/debug "Destructured %s into %s." namespace (mask namespaced-config)))
    namespaced-configs))

(defn remap-keys [input mapping]
  (merge
    (into {} (for [[new-key old-key] mapping
                   :let [old-value (get input old-key)]
                   :when old-value]
               [new-key old-value]))
    input))

(defn load-config
  "Loads the configuration from different sources and transforms it.

  Merges default config with environment variables:
  {:http-port 8080 :tokeninfo-url \"foo\"}, {:http-port 9090} -> {:http-port 9090 :tokeninfo-url \"foo\"}
  Then optionally renames some keys, but only if the new key does not exist:
  {:http-tokeninfo-url :tokeninfo-url}, {:http-port 9090 :tokeninfo-url \"foo\"}
    -> {:http-port 9090 :tokeninfo-url \"foo\" :http-tokeninfo-url \"foo\"}
  Then filters out by provided namespace prefixes:
  [:http], {:http-port 9090 :tokeninfo-url \"foo\" :http-tokeninfo-url \"foo\"}
    -> {:http-port 9090 :http-tokeninfo-url \"foo\"}
  Then extracts namespaces:
  {:http-port 9090 :http-tokeninfo-url \"foo\"} -> {:http {:port 9090 :tokeninfo-url \"foo\"}}"

  [default-config namespaces & [{:keys [mapping]}]]
  (-> (merge default-config environ/env)
      (remap-keys mapping)
      (parse-namespaces (conj namespaces :system))))

(defn load-configuration
  "Loads configuration with Zalando-specific tweaks (remapping and decryption) in place."
  ;; TODO move this function into the Zalando-specific library
  [namespaces default-configurations]
  (decrypt/decrypt-config
    (load-config
      (apply merge default-configurations)
      namespaces
      {:mapping {:http-tokeninfo-url     :tokeninfo-url
                 :oauth2-credentials-dir :credentials-dir}})))
