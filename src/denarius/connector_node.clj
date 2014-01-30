(ns denarius.connector-node
  (:use [clojure.tools.logging :only [info]])
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            denarius.engine-node))


(def default-connector-port 7892)

(def connector-options
  [["-p" "--port PORT" "Port number to listent to"
    :default default-connector-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-h" "--host HOST" "Engine host address"
    :default "localhost"
    :parse-fn identity]
   ["-e" "--engine-port PORT" "Engine port number to connect to"
    :default denarius.engine-node/default-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]])

(defn start-front-server [port]
  (info "Starting connector front server on port " port))

(defn create-back-channel [host port]
  (info "Starting connector back channel to engine " host ", port " port))

(defn start-connector [args]
  (info "Starting connector node")
  (let [{:keys [options arguments errors summary]} (parse-opts args 
                                                               connector-options)
        port   (:port options)
        e-port (:engine-port options)
        e-host (:host options)]
    (create-back-channel e-host e-port)
    (start-front-server port) ))