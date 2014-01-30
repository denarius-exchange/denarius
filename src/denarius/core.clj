(ns denarius.core
  (:use clojure.core
        [clojure.tools.logging :only [info]]
        denarius.engine-node
        denarius.connector-node)
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]) )


(def program-options
  [["-c" "--component COMPONENT" 
   "Specify if the instance should be an engine or a connector"
   :default "engine"
   :parse-fn identity
   :validate [#(case % "engine" true "connector" true false )
              "Must be engine or connector"]]
   ["-p" "--port PORT" "Port number to listent to"
    :default denarius.connector-node/default-connector-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-h" "--host HOST" "Engine host address"
    :default "localhost"
    :parse-fn identity]
   ["-e" "--engine-port PORT" "Engine port number to connect to"
    :default denarius.engine-node/default-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ;; A boolean option defaulting to nil
   ["-?" "--help" "Show help"]])

(defn usage [options-summary]
  (->> ["Denarius Financial Exchange."
        ""
        "Usage: [options]"
        ""
        "Options:"
        options-summary
        ""
        ;"Actions:"
        ;"  start    Start a new server"
        ;"  stop     Stop an existing server"
        ;"  status   Print a server's status"
        ;""
        ]
       (string/join \newline)
       println))


(defmulti start-denarius (fn [a & more] (:component a)) )

(defmethod start-denarius "engine" [options args]
  (info "Running Denarius Engine")
  (start-engine args) )

(defmethod start-denarius "connector" [options args]
  (info "Running Denarius Connector")
  (start-connector args) )

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args 
                                                               program-options)]
    (cond (:help options) (do (usage summary) (java.lang.System/exit 0)))
    (start-denarius options args) ))
