(ns org.zalando.stups.friboo.system.http-test
  (:require [clojure.test :refer :all]
            [org.zalando.stups.friboo.system.http :refer :all]))

(deftest test-flatten-parameters
  (is (= {:foo 1 :bar 2} (flatten-parameters {:parameters {:query {:foo 1} :path {:bar 2}}}))))

(deftest test-map-authorization-header-simple
  (is (= "xyz" (map-authorization-header "xyz")))
  (is (= "Bearer 123" (map-authorization-header "Token 123"))))

(deftest test-map-authorization-header-basic-auth
  (is (= "Bearer 123" (map-authorization-header "Basic b2F1dGgyOjEyMw=="))))

(defn foo-operation-with-args [params request dependency-1]
  [params request dependency-1])

(defn foo-operation-with-map [params request dependency-map]
  [params request dependency-map])

(deftest test-make-resolver-fn-with-deps
  (let [request {:parameters {:path {:pp-1 1}}}]
    (are [?result ?operation-fn-name ?resolver-factory]
      (= ?result (let [resolver-fn (?resolver-factory ['dep-1] ["dep-1-value"])
                       operation-fn (resolver-fn {"operationId" ?operation-fn-name})]
                   (operation-fn request)))
      [{:pp-1 1} request "dep-1-value"] (str `foo-operation-with-args) make-resolver-fn-with-deps-as-args
      [{:pp-1 1} request {:dep-1 "dep-1-value"}] (str `foo-operation-with-map) make-resolver-fn-with-deps-as-map)))

(deftest test-select-resolver-fn-maker
  (is (= make-resolver-fn-with-deps-as-args
         (select-resolver-fn-maker {})))
  (is (= "custom-maker"
         (select-resolver-fn-maker {:resolver-fn-maker "custom-maker"})))
  (is (= make-resolver-fn-with-deps-as-map
         (select-resolver-fn-maker {:dependencies-as-map true :resolver-fn-maker "custom-maker"}))))

(def-http-component HttpTest "foo.yaml" [db] :dependencies-as-map true)

(deftest test-def-http-component
  (let [http (map->HttpTest {:configuration "configuration" :db "db" :metrics "metrics" :audit-log "audit-log"})]
    (with-redefs [select-resolver-fn-maker (fn [opts]
                                             (is (= opts {:dependencies-as-map true}))
                                             make-resolver-fn-with-deps-as-map)
                  make-resolver-fn-with-deps-as-map (fn [dep-names dep-values]
                                                      (is (= ['db] dep-names))
                                                      (is (= ["db"] dep-values))
                                                      "resolver-fn")
                  start-component (fn [this metrics audit-log definition resolver-fn]
                                    (is (= [metrics audit-log definition resolver-fn]
                                           ["metrics" "audit-log" "foo.yaml" "resolver-fn"]))
                                    this)]
      (com.stuartsierra.component/start http))))
