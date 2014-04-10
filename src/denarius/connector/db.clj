(ns denarius.connector.db)


(defprotocol db-trades
  (init-trades-impl  [this])
  (insert-trade-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price])
  (query-trades-impl [this broker-id])
  (stop-trades-impl  [this]))


(defrecord nildb []
  db-trades
  (init-trades-impl  [this] nil)
  (insert-trade-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price] nil)
  (query-trades-impl [this broker-id] nil)
  (stop-trades-impl  [this] nil))


; Database name to be set via configuration
(def dbname (atom (nildb.)))


; Methods exposed by the namespace which encapsulate the multimethods and provide
; the dispatching first argument depending on the reference dbname
(defn init-trades [] (init-trades-impl @dbname) )

(defn insert-trade [broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
  (insert-trade-impl @dbname broker-id-1 order-id-1 broker-id-2 order-id-2 size price) )

(defn query-trades [broker-id]
  (query-trades-impl @dbname broker-id) )

(defn stop-trades [] (stop-trades-impl @dbname))


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