(ns denarius.connector.db.mockdb
  (:require [denarius.connector.db :as db]))


(def trades (atom {}))


(deftype mockdb []
  db/db-proto
  (init-trades-impl [this] nil)
  (insert-trade-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
    (let [newtrade      {:broker-id-1 broker-id-1 :order-id-1 order-id-1
                         :broker-id-2 broker-id-2 :order-id-2 order-id-2
                         :size size :price price}]
      (if-let [broker-trades (@trades broker-id-1)]
        (swap! broker-trades conj newtrade)
        (swap! trades assoc broker-id-1 (atom [newtrade]))
        )))
  (query-trades-impl [this broker-id]
    @(@trades broker-id))
  (stop-trades-impl [this] nil) )