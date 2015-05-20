(defproject org.zalando.stups/friboo "0.15.0-SNAPSHOT"
  :description "A utility library to write microservices in clojure."
  :url "https://github.com/zalando-stups/friboo"

  :license {:name "Apache 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo}

  :scm {:url "git@github.com:zalando-stups/friboo.git"}

  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [io.sarnowski/swagger1st "0.13.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [ring "1.3.2"]
                 [environ "1.0.0"]
                 [io.clj/logging "0.8.1"]
                 [org.apache.logging.log4j/log4j-api "2.3"]
                 [org.apache.logging.log4j/log4j-core "2.3"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.3"]
                 [org.apache.logging.log4j/log4j-jcl "2.3"]
                 [org.apache.logging.log4j/log4j-1.2-api "2.3"]
                 [org.apache.logging.log4j/log4j-jul "2.3"]
                 [com.jolbox/bonecp "0.8.0.RELEASE"]
                 [org.flywaydb/flyway-core "3.2.1"]
                 [amazonica "0.3.22"]
                 [org.clojure/data.codec "0.1.0"]
                 [overtone/at-at "1.2.0"]
                 [org.zalando.stups/tokens "0.1.0"]
                 [com.netflix.hystrix/hystrix-clj "1.4.7"]
                 [com.netflix.hystrix/hystrix-core "1.4.7"]]

  :plugins [[lein-cloverage "1.0.3"]]

  :pom-addition [:developers
                 [:developer {:id "sarnowski"}
                  [:name "Tobias Sarnowski"]
                  [:email "tobias.sarnowski@zalando.de"]
                  [:role "Maintainer"]
                  [:timezone "+1"]]]

  :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

  :deploy-repositories {"releases" {:url "https://oss.sonatype.org/service/local/staging/deploy/maven2/" :creds :gpg}
                        "snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/" :creds :gpg}})
