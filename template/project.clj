(defproject friboo/lein-template "0.3.0-SNAPSHOT"
  :description "Leiningen template for Friboo library"
  :url "https://github.com/dryewo/friboo-template"
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/"}
  :eval-in-leiningen true
  :deploy-repositories [["releases" :clojars]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/java.classpath "0.2.3"]
                                  [midje "1.8.3"]]}})
