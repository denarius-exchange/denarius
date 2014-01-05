(ns denarius.order
  (:use clojure.core))


; Record for order information
(defrecord Order [order-id broker-id type side size price])

(defn create-order [order-id broker-id type side size price]
  "Create a new order with order-id, customer id (broker), side, size and
   price information"
  (->Order order-id broker-id type side size price ))

(defn create-order-ref [order-id broker-id
                        type side size price key watcher]
  (let [order (create-order order-id broker-id type side size price)]
    (add-watch (ref order) key watcher )))


(def last-oder-id (ref 0))

(defn get-order-id []
  (dosync (alter last-oder-id inc)))

