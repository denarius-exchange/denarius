(ns denarius.connector.db-orders)

(defprotocol db-orders
  (init-orders-impl  [this])
  (insert-order-impl [this order])
  (query-orders-impl [this broker-id])
  (alter-size-impl   [this broker-id order-id new-size])
  (remove-order-impl [this broker-id order-id])
  (stop-orders-impl  [this]))


(defrecord nilorders []
  db-orders
  (init-orders-impl  [this] nil)
  (insert-order-impl [this order] nil)
  (query-orders-impl [this broker-id] nil)
  (alter-size-impl   [this broker-id order-id new-size] nil)
  (remove-order-impl [this broker-id order-id] nil)
  (stop-orders-impl  [this] nil))

; Order database name to be set via configuration
(def dborders (atom (nilorders.)))

(defn init-orders  [] (init-orders-impl @dborders))
(defn insert-order [order] (insert-order-impl @dborders order))
(defn query-orders [broker-id] (query-orders-impl @dborders broker-id))
(defn remove-order [broker-id order-id] (remove-order-impl @dborders broker-id order-id))
(defn alter-size   [broker-id order-id new-size] (alter-size-impl @dborders broker-id order-id new-size))
(defn stop-orders  [] (stop-orders-impl @dborders))