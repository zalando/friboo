(ns leiningen.new.friboo
  (:require [leiningen.new.templates :refer [renderer year date project-name
                                             ->files sanitize-ns name-to-path
                                             multi-segment]]
            [leiningen.core.main :as main]
            [clojure.string :as str]))

(defn db-prefix [name]
  (->> (str/split name #"(-|_)")
       (map first)
       (apply str)))

(defn prepare-data [name]
  (let [namespace (sanitize-ns name)]
    {:raw-name    name
     :name        (project-name name)
     :namespace   namespace
     :nested-dirs (name-to-path namespace)
     :db-prefix   (db-prefix (project-name name))
     :year        (year)
     :date        (date)}))

(defn friboo
  "A Friboo project template"
  [name]
  (let [render (renderer "friboo")
        data   (prepare-data name)]
    (main/debug "Template data:" data)
    (main/info "Generating a project called" name "based on the 'friboo' template.")
    (->files data
             ["project.clj" (render "project.clj" data)]
             ["README.md" (render "README.md" data)]
             [".gitignore" (render "gitignore" data)]
             ["dev-config.edn" (render "dev-config.edn" data)]
             ["resources/api/api.yaml" (render "api.yaml" data)]
             ["dev/user.clj" (render "user.clj" data)]
             ["src/{{nested-dirs}}/controller.clj" (render "controller.clj" data)]
             ["src/{{nested-dirs}}/core.clj" (render "core.clj" data)]
             ["test/{{nested-dirs}}/core_test.clj" (render "core_test.clj" data)]
             ["test/{{nested-dirs}}/controller_test.clj" (render "controller_test.clj" data)]
             "resources")))
