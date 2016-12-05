(ns org.zalando.stups.friboo.dev-test
  (:require
    [clojure.test :refer :all]
    [midje.sweet :refer :all]
    [org.zalando.stups.friboo.dev :refer :all]))

(deftest wrap-midje-facts

  (facts "about slurp-if-exists"
    (slurp-if-exists "nonexistent-foo") => nil
    (slurp-if-exists "test/org/zalando/stups/friboo/test.edn") => "{:foo \"bar\"}\n")

  (facts "about load-dev-config"
    (load-dev-config "nonexistent-foo") => nil
    (load-dev-config "test/org/zalando/stups/friboo/test.edn") => {:foo "bar"})

  (facts "about get-free-port"
    (repeatedly 100 get-free-port) => (has every? #(< 1024 % 65536)))

  )
