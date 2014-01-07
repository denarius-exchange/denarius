(defproject denarius "0.1.0-SNAPSHOT"
  :description "Denarius is a an open-source financial exchange."
  :url "https://github.com/analyticbastard/denarius"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [ring "1.2.1"]
                 [http-kit "2.1.13"]
                 [compojure "1.1.6"]
                 [org.clojure/data.json "0.2.3"]]
  :main denarius.core)
