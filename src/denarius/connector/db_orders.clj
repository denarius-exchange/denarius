(ns denarius.connector.db-orders)

(defprotocol db-orders
  (init-orders-impl   [this])
  (insert-order-impl  [this order])
  (query-orders-impl  [this broker-id])
  (query-order-impl   [this broker-id order-id])
  (alter-size-impl    [this broker-id order-id new-size])
  (decrease-size-impl [this broker-id order-id amount])
  (remove-order-impl  [this broker-id order-id])
  (stop-orders-impl   [this]))


(defrecord nilorders []
  db-orders
  (init-orders-impl   [this] nil)
  (insert-order-impl  [this order] nil)
  (query-orders-impl  [this broker-id] nil)
  (query-order-impl   [this broker-id order-id] nil)
  (alter-size-impl    [this broker-id order-id new-size] nil)
  (decrease-size-impl [this broker-id order-id amount] nil)
  (remove-order-impl  [this broker-id order-id] nil)
  (stop-orders-impl   [this] nil))

; Order database name to be set via configuration
(def dbname (atom (nilorders.)))

(defn init-orders  [] (init-orders-impl @dbname))
(defn insert-order [order] (insert-order-impl @dbname order))
(defn query-orders [broker-id] (query-orders-impl @dbname broker-id))
(defn query-order  [broker-id order-id] (query-order-impl @dbname broker-id order-id))
(defn remove-order [broker-id order-id] (remove-order-impl @dbname broker-id order-id))
(defn alter-size   [broker-id order-id new-size] (alter-size-impl @dbname broker-id order-id new-size))
(defn decrease-size [broker-id order-id amount] (decrease-size-impl @dbname broker-id order-id amount))
(defn stop-orders  [] (stop-orders-impl @dbname))