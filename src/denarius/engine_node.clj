(ns denarius.engine-node
  (:use clojure.core
        [clojure.tools.logging :only [info]]
        denarius.order
        denarius.engine)
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            denarius.tcp))


(def default-port 7891)

(def engine-options
  [["-p" "--port PORT" "Port number to listent to"
    :default default-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]])


(def book (ref (create-order-book "EUR")))

(defn cross-function [order-ref-1 order-ref-2 size price]
  "Function called on order-matching. Callbacks added to the vector
   :on-matching will be called."
  ; first make changes persistent. If impossible, return false
  (future
    (let [more (:on-matching (meta @order-ref-1))]
      (doall (map #(% order-ref-1 order-ref-2 size price) more)) )
    (let [more (:on-matching (meta @order-ref-2))]
      (doall (map #(% order-ref-2 order-ref-1 size price) more)) )
    )
  ; return true on no error
  true)


(defn start-brokering-interfaces [port]
  "Starts the server for connecting to connector nodes"
  (info "Starting brokering interfaces")
  ;(denarius.http/start-http book))
  (denarius.tcp/start-tcp book port))


(defn start-engine [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args 
                                                               engine-options)
        port (:port options)]
    (start-brokering-interfaces port)
    (start-matching-loop book cross-function) ))
