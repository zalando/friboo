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
                  :package     "foo"
                  :db-prefix   "f"})
    (prepare-data "foo-bar")
    => (contains {:raw-name    "foo-bar"
                  :name        "foo-bar"
                  :namespace   "foo-bar"
                  :package     "foo_bar"
                  :nested-dirs "foo_bar"
                  :db-prefix   "fb"})
    (prepare-data "foo/bar")
    => (contains {:raw-name    "foo/bar"
                  :name        "bar"
                  :namespace   "foo.bar"
                  :package     "foo.bar"
                  :nested-dirs "foo/bar"
                  :db-prefix   "b"})
    (prepare-data "foo.baz/aaa-bbb")
    => (contains {:raw-name    "foo.baz/aaa-bbb"
                  :db-prefix   "ab"
                  :name        "aaa-bbb"
                  :namespace   "foo.baz.aaa-bbb"
                  :package     "foo.baz.aaa_bbb"
                  :nested-dirs "foo/baz/aaa_bbb"}))
  )
