(ns leiningen.new.friboo-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [leiningen.new.friboo :refer :all]))

(deftest test-prepare-data
  (facts ""
    (prepare-data "foo")
    => (contains {:raw-name    "foo"
                  :name        "foo"
                  :namespace   "foo"
                  :nested-dirs "foo"
                  :db-prefix   "f"})
    (prepare-data "foo-bar")
    => (contains {:raw-name    "foo-bar"
                  :name        "foo-bar"
                  :namespace   "foo-bar"
                  :nested-dirs "foo_bar"
                  :db-prefix   "fb"})
    (prepare-data "foo/bar")
    => (contains {:raw-name    "foo/bar"
                  :name        "bar"
                  :namespace   "foo.bar"
                  :nested-dirs "foo/bar"
                  :db-prefix   "b"})
    (prepare-data "foo.baz/bar")
    => (contains {:raw-name    "foo.baz/bar"
                  :name        "bar"
                  :namespace   "foo.baz.bar"
                  :nested-dirs "foo/baz/bar"
                  :db-prefix   "b"}))
  )
