(ns {{namespace}}.api-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [{{namespace}}.api :refer :all]))

(deftest can-get-hello
  (is (= (get-hello {:configuration {:example-param "foo"}} {:name "Friboo"} nil)
         {:status  200
          :headers {}
          :body    {:message "Hello Friboo" :details {:X-friboo "foo"}}})))
