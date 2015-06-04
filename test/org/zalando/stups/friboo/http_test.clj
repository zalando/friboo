(ns org.zalando.stups.friboo.http-test
  (:require
    [clojure.test :refer :all]
    [org.zalando.stups.friboo.system.http :refer :all]))

(deftest test-map-authorization-header-simple
  (is (= "xyz" (map-authorization-header "xyz")))
  (is (= "Bearer 123" (map-authorization-header "Token 123"))))

(deftest test-map-authorization-header-basic-auth
  (is (= "Bearer 123" (map-authorization-header "Basic b2F1dGgyOjEyMw=="))))

(deftest test-add-logs-to-empty-list
  (let [logs (ref [])
        new-logs ["foo" "bar"]]
    (add-logs logs new-logs)
    (is (= @logs ["foo" "bar"]))))

(deftest test-add-logs-to-existing-ones
  (let [logs (ref ["bla"])
        new-logs ["foo" "bar"]]
    (add-logs logs new-logs)
    (is (= @logs ["bla" "foo" "bar"]))))

(deftest test-add-no-logs-to-existing-ones
  (let [logs (ref ["bla"])
        new-logs []]
    (add-logs logs new-logs)
    (is (= @logs ["bla"]))))

(deftest test-empty-empty-logs
  (let [logs (ref [])
        previous-logs (empty-logs logs)]
    (is (empty? @logs))
    (is (empty? previous-logs))))

(deftest test-empty-logs
  (let [logs (ref ["foo" "bar"])
        previous-logs (empty-logs logs)]
    (is (empty? @logs))
    (is (= ["foo" "bar"]))))

(deftest test-is-modifying
  (let [methods [:post :put :patch :delete]]
    (doseq [method methods]
      (is (is-modifying? {:request-method method})))))

(deftest test-is-non-modifying
  (let [methods [:get :head :options]]
    (doseq [method methods]
      (is (not (is-modifying? {:request-method method}))))))

(deftest test-collect-audit-logs
  (let [dummy-response {:status 200 :body "foobar"}
        dummy-request {:swagger        "will be erased"
                       :configuration  "will be erased"
                       :body           "will be erased"
                       :tokeninfo      {"access_token" "will be erased"
                                        "uid"          "testuser"}
                       :headers        {"authorization" "will be erased"
                                        "content-type"  "application/json"}
                       :parameters     {:foo "bar"}
                       :request-method :post}
        next-handler (constantly dummy-response)
        logs (ref [])
        enabled? true
        handler-fn (collect-audit-logs next-handler logs enabled?)]
    (is (= dummy-response (handler-fn dummy-request)))
    (is (seq @logs))
    (let [line (first @logs)]
      (is (= (set (keys line)) #{:tokeninfo :headers :parameters :request-method :logged-on})))))

(deftest test-collect-no-audit-logs-when-no-success
  (let [dummy-response {:status 400 :body "foobar"}
        dummy-request {:swagger        "will be erased"
                       :configuration  "will be erased"
                       :body           "will be erased"
                       :tokeninfo      {"access_token" "will be erased"
                                        "uid"          "testuser"}
                       :headers        {"authorization" "will be erased"
                                        "content-type"  "application/json"}
                       :parameters     {:foo "bar"}
                       :request-method :post}
        next-handler (constantly dummy-response)
        logs (ref [])
        enabled? true
        handler-fn (collect-audit-logs next-handler logs enabled?)]
    (is (= dummy-response (handler-fn dummy-request)))
    (is (empty? @logs))))


(deftest test-collect-no-audit-logs-when-not-modifying
  (let [dummy-response {:status 200 :body "foobar"}
        dummy-request {:swagger        "will be erased"
                       :configuration  "will be erased"
                       :body           "will be erased"
                       :tokeninfo      {"access_token" "will be erased"
                                        "uid"          "testuser"}
                       :headers        {"authorization" "will be erased"
                                        "content-type"  "application/json"}
                       :parameters     {:foo "bar"}
                       :request-method :get}
        next-handler (constantly dummy-response)
        logs (ref [])
        enabled? true
        handler-fn (collect-audit-logs next-handler logs enabled?)]
    (is (= dummy-response (handler-fn dummy-request)))
    (is (empty? @logs))))

