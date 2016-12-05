(ns {{namespace}}.core-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.dev :as dev]
            [com.stuartsierra.component :as component]
            [clj-http.client :as http]
            [{{namespace}}.core :refer :all]))

(deftest test-core-system

  (facts "about run"
    (let [dev-config  (dev/load-dev-config "./dev-config.edn")
          test-config (merge {:http-port (dev/get-free-port)}
                             dev-config)
          system      (run test-config)
          port        (-> system :http :configuration :port)]
      (try
        (facts "In the beginning there are no memories"
          (http/get (str "http://localhost:" port "/hello/Friboo") {:as :json})
          => (contains {:status 200 :body {:details {:X-friboo "foo"}, :message "Hello Friboo"}}))
        (finally
          (component/stop system)))))

  )
