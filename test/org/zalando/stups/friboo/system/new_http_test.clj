(ns org.zalando.stups.friboo.system.new-http-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [org.zalando.stups.friboo.system.new-http :refer :all :as new-http]
            [com.stuartsierra.component :as component]
            [clj-http.client :as http]
            [clojure.pprint :refer [pprint]]
            [org.zalando.stups.friboo.test-utils :as u])
  (:import (com.netflix.hystrix.exception HystrixRuntimeException HystrixRuntimeException$FailureType)))

(deftest unit-tests

  (facts "about merged-parameters"
    (merged-parameters {:parameters {:query {:foo 1} :path {:bar 2}}}) => {:foo 1 :bar 2})

  (facts "about flatten1"
    (flatten1 {:a 1 :b [2 3]}) => [:a 1 :b [2 3]])

  (facts "about with-flattened-options-map"
    (-> :first-arg
        (#'new-http/with-flattened-options-map vector {:a 1 :b 2})) => (vector :first-arg :a 1 :b 2))

  )

(defmacro with-test-http
  "Helper for running tests against working component.
  Picks a random port, starts the component and in the end always stops it."
  [[url-sym filename] & body]
  `(let [port#      (u/get-free-port)
         http-comp# (-> (str "org/zalando/stups/friboo/system/" ~filename)
                        (make-http {:port port#})
                        (component/start))
         ~url-sym (str "http://localhost:" port#)]
     (try
       ~@body
       (finally
         (component/stop http-comp#)))))

(defn get-info [controller params request]
  )

(defn put-foo [controller params request]
  )

(defchecker contains-keys [expected-keys]
  (checker [actual]
    (clojure.set/superset? (set (keys actual)) (set expected-keys))))

(deftest component-tests

  (facts "Default behavior"

    (with-test-http [url "new_http.yml"]
      (fact "Redirects to /ui/ from /"
        (http/get (str url) {:follow-redirects false})
        => (contains {:status 302 :headers (contains {"Location" "/ui/"})})))

    (with-test-http [url "new_http.yml"]
      (fact "favicon.ico requests are 404ed"
        (http/get (str url "/favicon.ico") {:throw-exceptions false})
        => (contains {:status 404})))

    (with-test-http [url "new_http.yml"]
      (fact "Default headers are included in the response."
        (http/get (str url "/info"))
        => (contains {:headers (contains {"Strict-Transport-Security"    anything
                                          "Access-Control-Allow-Methods" anything
                                          "Content-Type"                 "application/json"})})))

    (with-test-http [url "new_http.yml"]
      (fact "Health endpoint is provided"
        (http/get (str url "/.well-known/health") {:as :json})
        => (contains {:status 200 :body {:health true}})))

    (with-test-http [url "new_http.yml"]
      (fact "Hystrix exceptions are converted"
        (http/get (str url "/info") {:throw-exceptions false :as :json :coerce :always})
        => (contains {:status 503})
        (provided
          (get-info anything anything anything)
          =throws=> (HystrixRuntimeException. HystrixRuntimeException$FailureType/TIMEOUT nil
                                              "Divide by zero" (ArithmeticException. "foo") nil))))
    (with-test-http [url "new_http.yml"]
      (fact "ex-info exceptions are not changed"
        (http/get (str url "/info") {:throw-exceptions false :as :json :coerce :always})
        => (contains {:status 501})
        (provided
          (get-info anything anything anything) =throws=> (ex-info "Hello" {:foo "bar" :http-code 501}))))

    (with-test-http [url "new_http.yml"]
      (fact "Unhandled exceptions are nicely packaged, details are provided in the response body."
        (http/get (str url "/info") {:throw-exceptions false :as :json :coerce :always})
        => (contains {:status 500 :body {:details "java.lang.ArithmeticException: Divide by zero"
                                         :message "Internal server error"}})
        (provided
          (get-info anything anything anything) =throws=> (ArithmeticException. "Divide by zero"))))

    )

  (with-test-http [url "new_http.yml"]
    (fact "Print request contents"
      (http/get (str url "/info") {:follow-redirects false})
      => (contains {:status 200})))

  (fact "About controllers"
    (let [port      (u/get-free-port)
          http-comp (-> (make-http (str "org/zalando/stups/friboo/system/" "new_http.yml") {:port port})
                        ;; Instead of relying on component/using and starting the system, provide the dependency directly
                        (merge {:controller ..controller..})
                        (component/start))
          url       (str "http://localhost:" port)]
      (try
        (fact "When controller is provided as a dependency, it is passed as the first parameter the handling functions"
          (http/get (str url "/info") {:follow-redirects false})
          => (contains {:status 200 :body "OK"})
          (provided (get-info ..controller.. anything anything) => {:body "OK"}))
        (finally
          (component/stop http-comp)))))

  (with-test-http [url "new_http.yml"]
    (with-redefs [get-info (fn [controller _ request]
                             (fact "Controller is nil with default configuration"
                               controller => nil)
                             (fact "Raw request object contains all the things"
                               (keys request) => (contains [:ssl-client-cert
                                                            :protocol
                                                            :remote-addr
                                                            :params
                                                            :headers
                                                            :server-port
                                                            :content-length
                                                            :form-params
                                                            :query-params
                                                            :content-type
                                                            :character-encoding
                                                            :swagger
                                                            :configuration
                                                            :uri
                                                            :server-name
                                                            :query-string
                                                            :body
                                                            :parameters
                                                            :scheme
                                                            :request-method]))
                             {:body {:ok true}})]
      (fact "Normal requests return json-encoded body."
        (http/get (str url "/info") {:as :json})
        => (contains {:status 200 :body {:ok true}}))))

  (with-test-http [url "new_http.yml"]
    (with-redefs [put-foo (fn [_ params _]
                            (fact "Parameters are taken from path, query and body and flattened."
                              params => {:foo_id "123" :page 1 :foo {:foo_text "lalala"}})
                            {:status 201 :body {:ok true}})]
      (fact "Put request works"
        (http/put (str url "/foo/123?page=1") {:content-type :json
                                               :form-params  {:foo_text "lalala"}
                                               :as           :json})
        => (contains {:status 201 :body {:ok true}}))))

  )
