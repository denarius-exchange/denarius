(ns denarius.core
  (:use clojure.core
        [clojure.tools.logging :only [info]]
        denarius.order
        denarius.engine)
  (:require denarius.tcp) )


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


(defn start-brokering-interfaces []
  "Starts the server for connecting to connector nodes"
  (info "Starting brokering interfaces")
  ;(denarius.http/start-http book))
  (denarius.tcp/start-tcp book))


(defn -main [& args]
  (info "Running Denarius")
  (start-brokering-interfaces)
  (start-matching-loop book cross-function) )