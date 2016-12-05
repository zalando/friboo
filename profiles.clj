{:test {:dependencies [[midje "1.8.3"]
                       [org.postgresql/postgresql "9.4.1212"]]
        :env          {:tokeninfo-url "default-tokeninfo"}}
 :dev  {:repl-options {:init-ns user}
        :source-paths ["dev"]
        :dependencies [[midje "1.8.3"]
                       [org.postgresql/postgresql "9.4.1212"]
                       [org.clojure/tools.namespace "0.2.11"]
                       [org.clojure/java.classpath "0.2.3"]]}}
