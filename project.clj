(defproject org.zalando.stups/friboo "1.5.4-SNAPSHOT"
  :description "A utility library to write microservices in clojure."
  :url "https://github.com/zalando-stups/friboo"

  :license {:name "Apache 2.0"
            :url  "http://www.apache.org/licenses/LICENSE-2.0"
            :distribution :repo}

  :scm {:url "git@github.com:zalando-stups/friboo.git"}

  :min-lein-version "2.0.0"

  :java-source-paths ["java"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [io.sarnowski/swagger1st "0.20.0"]
                 [org.zalando.stups/txdemarcator "0.7.0"]
                 [com.stuartsierra/component "0.3.0"]
                 [ring "1.4.0"]
                 [org.eclipse.jetty/jetty-servlet "9.2.10.v20150310"]
                 [amalloy/ring-gzip-middleware "0.1.3"]
                 [environ "1.0.1"]
                 [io.clj/logging "0.8.1"]
                 [org.apache.logging.log4j/log4j-api "2.4.1"]
                 [org.apache.logging.log4j/log4j-core "2.4.1"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.4.1"]
                 [org.apache.logging.log4j/log4j-jcl "2.4.1"]
                 [org.apache.logging.log4j/log4j-1.2-api "2.4.1"]
                 [org.apache.logging.log4j/log4j-jul "2.4.1"]
                 [com.jolbox/bonecp "0.8.0.RELEASE"]
                 [org.flywaydb/flyway-core "3.2.1"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [amazonica "0.3.39" :exclusions [org.apache.httpcomponents/httpclient joda-time]]
                 [org.clojure/data.codec "0.1.0"]
                 [overtone/at-at "1.2.0"]
                 [org.zalando.stups/tokens "0.9.2"]
                 [com.netflix.hystrix/hystrix-clj "1.4.21"]
                 [com.netflix.hystrix/hystrix-core "1.4.21"]
                 [com.netflix.hystrix/hystrix-metrics-event-stream "1.4.21"]
                 [org.clojure/core.incubator "0.1.3"]
                 [metrics-clojure "2.5.1" :exclusions [io.dropwizard.metrics/metrics-core]]
                 [io.dropwizard.metrics/metrics-servlets "3.1.2"]
                 [org.slf4j/slf4j-api "1.7.13"]
                 [com.fasterxml.jackson.core/jackson-core "2.6.3"]
                 [com.fasterxml.jackson.core/jackson-databind "2.6.3"]
                 [org.apache.httpcomponents/httpclient "4.5.1"]
                 [org.clojure/core.memoize "0.5.8"]
                 [commons-codec "1.10"]
                 [com.newrelic.agent.java/newrelic-api "3.22.1"]]

  :plugins [[lein-cloverage "1.0.6"]]

  :pom-addition [:developers
                 [:developer {:id "sarnowski"}
                  [:name "Tobias Sarnowski"]
                  [:email "tobias.sarnowski@zalando.de"]
                  [:role "Maintainer"]
                  [:timezone "+1"]]]

  :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

  :deploy-repositories {"releases" {:url "https://oss.sonatype.org/service/local/staging/deploy/maven2/" :creds :gpg}
                        "snapshots" {:url "https://oss.sonatype.org/content/repositories/snapshots/" :creds :gpg}})
