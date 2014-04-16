(ns denarius.connector.db-trades)

(defprotocol db-trades
  (init-trades-impl  [this])
  (insert-trade-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price])
  (query-trades-impl [this broker-id])
  (stop-trades-impl  [this]))

(defrecord db-trades-nil [dbopt]
  db-trades
  (init-trades-impl  [this] nil)
  (insert-trade-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price] nil)
  (query-trades-impl [this broker-id] nil)
  (stop-trades-impl  [this] nil))

; Database name to be set via configuration
(def dbname (atom (db-trades-nil. nil)))

; Methods exposed by the namespace which encapsulate the multimethods and provide
; the dispatching first argument depending on the reference dbname
(defn init-trades [] (init-trades-impl @dbname) )

(defn insert-trade [broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
  (insert-trade-impl @dbname broker-id-1 order-id-1 broker-id-2 order-id-2 size price) )

(defn query-trades [broker-id]
  (query-trades-impl @dbname broker-id) )

(defn stop-trades [] (stop-trades-impl @dbname))

