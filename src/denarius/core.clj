(ns denarius.core
  (:use clojure.core
        [clojure.tools.logging :only [info]]
        denarius.engine)
  (:require denarius.http) )


(def book (ref (create-order-book "EUR")))

(def matching-agent (agent 1))

(defn cross-function [order-ref-1 order-ref-2 size price]
  ;(println price size)
  )


(defn start-matching-loop []
  (send-off matching-agent
            (fn [agent-value]
              (while true
                (do ;(java.lang.Thread/sleep 1)
                  (try
                   (match-once @book cross-function)
                   (catch Exception e (println e)))
                   )))))


(defn start-brokering-interfaces []
  (info "Starting brokering interfaces")
  (denarius.http/start-http book))


(defn -main [& args]
  (info "Running Denarius")
  (start-brokering-interfaces)
  (start-matching-loop) )