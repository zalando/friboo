(defproject org.zalando/friboo "0.1.0-SNAPSHOT"
  :description "A utility library to write microservices in clojure."
  :url "https://github.com/zalando-stups/friboo"

  :license {:name "Apache 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [io.sarnowski/swagger1st "0.4.0-SNAPSHOT"]
                 [com.stuartsierra/component "0.2.3"]
                 [ring "1.3.2"]
                 [environ "1.0.0"]
                 [org.apache.logging.log4j/log4j-api "2.2"]
                 [org.apache.logging.log4j/log4j-core "2.2"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.2"]
                 [org.apache.logging.log4j/log4j-jcl "2.2"]
                 [org.apache.logging.log4j/log4j-1.2-api "2.2"]
                 [org.apache.logging.log4j/log4j-jul "2.2"]
                 [com.jolbox/bonecp "0.8.0.RELEASE"]
                 [amazonica "0.3.19"]
                 [org.clojure/data.codec "0.1.0"]])
