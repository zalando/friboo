(ns org.zalando.stups.friboo.zalando-specific.system-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.zalando-specific.config :as config]
            [org.zalando.stups.friboo.zalando-specific.system.http :as http]
            [com.stuartsierra.component :as component]
            [org.zalando.stups.friboo.test-utils :as u]
            [org.zalando.stups.friboo.system.metrics :as metrics]
            [org.zalando.stups.friboo.system :as system]
            [clj-http.client :as client]
            [org.zalando.stups.friboo.zalando-specific.auth :as auth]
            [org.zalando.stups.friboo.system.mgmt-http :as mgmt-http]
            [org.zalando.stups.friboo.system.oauth2 :as oauth2]
            [org.zalando.stups.friboo.system.audit-logger.http :as audit-http]))

(defrecord Controller [configuration]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))

;; Example of the system that can be used in Zalando projects
(defn zalando-run [args-configuration]
  (let [configuration (config/load-config
                        (merge {:system-stups-log-level "INFO"
                                :system-log-level       "INFO"
                                :oauth2-access-tokens   "http-audit-logger=12345678910"}
                               args-configuration)
                        [:http :metrics :controller :auth :mgmt-http :system])
        system        (component/map->SystemMap
                        {:http              (component/using
                                              (http/make-zalando-http
                                                "org/zalando/stups/friboo/system/new_http.yml"
                                                (:http configuration)
                                                ;; TODO this is quite ugly, need to find a way to pass TOKENINFO_URL
                                                ;; to Http component
                                                (-> configuration :global :tokeninfo-url))
                                              [:metrics :controller])
                         :metrics           (metrics/map->Metrics {:configuration (:metrics configuration)})
                         :auth              (auth/map->Authorizer {:configuration (:auth configuration)})
                         :mgmt-http         (component/using
                                              (mgmt-http/map->MgmtHTTP {:configuration (:mgmt-http configuration)})
                                              [:metrics])
                         :controller        (component/using
                                              (map->Controller {:configuration (:controller configuration)})
                                              [:auth])
                         :tokens            (oauth2/map->OAuth2TokenRefresher {:configuration (:oauth2 configuration)
                                                                               :tokens        {:http-audit-logger ["uid"]}})
                         :http-audit-logger (component/using
                                              (audit-http/map->HTTP {:configuration (:auditlogger configuration)})
                                              [:tokens])
                         ;; TODO Setup PostgreSQL for tests and enable this:
                         ;:db                (sql/map->DB {:configuration (:db configuration)})
                         })]
    (system/run configuration system)))

(deftest works

  (let [system (zalando-run {:http-port      (u/get-free-port)
                             :mgmt-http-port (u/get-free-port)
                             :tokeninfo-url  "tokeninfo-url"})]
    (try
      ;; We just want to make sure that the whole system starts up successfully
      ;; Perform some basic smoke test
      (fact "tokeninfo-url is treated properly"
        (client/get (str "http://localhost:" (-> system :http :configuration :port) "/info")
                    {:throw-exceptions false})
        => (contains {:status 401}))
      (fact "Statically provided access token returned"
        (oauth2/access-token :http-audit-logger (:tokens system)) => "12345678910")
      (finally
        (component/stop system))))

  )
