(defproject denarius "0.1.0-SNAPSHOT"
  :description "Denarius is a an open-source financial exchange."
  :url "https://github.com/analyticbastard/denarius"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.json "0.2.3"]
                 [aleph "0.3.0"]
                 [org.clojure/tools.cli "0.3.1"]]
  :main denarius.core)
