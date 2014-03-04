(ns denarius.connector.db)


(defprotocol db-proto
  (init-impl [this])
  (insert-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price])
  (stop-impl [this]))


(defrecord nildb []
  db-proto
  (init-impl [this] nil)
  (insert-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price] nil)
  (stop-impl [this] nil))


; Database name to be set via configuration
(def dbname (atom (nildb.)))


; Methods exposed by the namespace which encapsulate the multimethods and provide
; the dispatching first argument depending on the reference dbname
(defn init [] (init-impl @dbname) )

(defn insert [broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
  (insert-impl @dbname broker-id-1 order-id-1 broker-id-2 order-id-2 size price) )

(defn stop [] (stop-impl @dbname))
