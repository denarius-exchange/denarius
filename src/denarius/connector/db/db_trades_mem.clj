(ns denarius.connector.db.db-trades-mem
  (:require [denarius.connector.db-trades :as db]))

(def trades (atom {}))

(deftype db-trades-mem [dbopt]
  db/db-trades
  (init-trades-impl [this] (reset! trades {}))
  (insert-trade-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
    (let [newtrade      {:broker-id-1 broker-id-1 :order-id-1 order-id-1
                         :broker-id-2 broker-id-2 :order-id-2 order-id-2
                         :size size :price price}]
      (if-let [broker-trades (@trades broker-id-1)]
        (swap! broker-trades conj newtrade)
        (swap! trades assoc broker-id-1 (atom [newtrade]))  )))
  (query-trades-impl [this broker-id]
    (if-let [broker-trades (@trades broker-id)]
      @broker-trades))
  (stop-trades-impl [this] nil) )




