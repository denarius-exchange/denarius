(defproject denarius "0.1.0-SNAPSHOT"
  :description "Denarius is a an open-source financial exchange."
  :url "https://github.com/analyticbastard/denarius"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.json "0.2.3"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/java.jdbc "0.3.3"]
                 [aleph "0.3.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [sonian/carica "1.0.4" :exclusions [[cheshire]]]
                 [org.zeromq/jeromq "0.3.3"]    ;; Remove if using the faster JNI version jzmq. See Wiki/Readme.md
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]
                 [mysql/mysql-connector-java "5.1.25"]]
  :jvm-opts ["-Djava.library.path=lib/"]
  :main denarius.core)
