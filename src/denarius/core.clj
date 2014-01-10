(ns denarius.core
  (:use clojure.core
        [clojure.tools.logging :only [info]]
        denarius.order
        denarius.engine)
  (:require denarius.http) )


(def book (ref (create-order-book "EUR")))

(def matching-agent (agent 1))

(defn cross-function [order-ref-1 order-ref-2 size price]
  ; first make changes persistent. If impossible, return false
  (future
    (let [more (:on-matching (meta @order-ref-1))]
      (doall (map #(% order-ref-1 order-ref-2 size price) more)) )
    (let [more (:on-matching (meta @order-ref-2))]
      (doall (map #(% order-ref-2 order-ref-1 size price) more)) )
    )
  ; return true on no error
  true)

(defn start-matching-loop []
  (let [cross-function cross-function]
    (send-off matching-agent
              (fn [agent-value]
                (while true
                  (do ;(java.lang.Thread/sleep 1)
                    (try
                      (match-once @book cross-function )
                      (catch Exception e))
                    ))))))


(defn start-brokering-interfaces []
  (info "Starting brokering interfaces")
  (denarius.http/start-http book))


(defn -main [& args]
  (info "Running Denarius")
  (start-brokering-interfaces)
  (start-matching-loop) )