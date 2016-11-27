(defproject friboo/lein-template "2.0.0-beta1-SNAPSHOT"
  :description "Leiningen template for Friboo library"
  :url "https://github.com/zalando/friboo/template"
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/"}
  :eval-in-leiningen true
  :deploy-repositories [["releases" :clojars]]
  :plugins [[lein-shell "0.5.0"]]
  :release-tasks [["shell" "git" "diff" "--exit-code"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :vcs :git
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/java.classpath "0.2.3"]
                                  [midje "1.8.3"]]}})
