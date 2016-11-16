; Copyright © 2016 Zalando SE
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

(ns org.zalando.stups.friboo.zalando-specific.config
  (:require [environ.core :as environ]
            [org.zalando.stups.friboo.log :as log]
            [clojure.string :as str]
            [org.zalando.stups.friboo.config :as config]
            [org.zalando.stups.friboo.config-decrypt :as decrypt]))

(defn load-config
  "Loads configuration with Zalando-specific tweaks (remapping and decryption) in place."
  [default-config additional-namespaces & [{:keys [mapping]}]]
  (decrypt/decrypt-config
    (config/load-config
      default-config
      (concat [:global :oauth2] additional-namespaces)
      {:mapping (merge {:global-tokeninfo-url   :tokeninfo-url
                        :oauth2-credentials-dir :credentials-dir}
                       mapping)})))
