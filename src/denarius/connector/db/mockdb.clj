(ns denarius.connector.db.mockdb
  (:require [denarius.connector.db :as db]))


(def trades (atom {}))


(deftype mockdb []
  db/db-trades
  (init-trades-impl [this] (reset! trades []))
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


(def orders (atom []))

(deftype memorders []
  db-orders
  (init-orders-impl  [this] (reset! orders {}))
  (insert-order-impl [this broker-id order]
    (let [order-id (:order-id order)]
      (if-let [broker-orders (@orders broker-id)]
        (swap! broker-orders assoc broker-id (atom {order-id (atom order)}))
        (swap! orders assoc broker-id (atom {order-id (atom order)}))
        )))
  (query-orders-impl [this broker-id] nil)
  (alter-size-impl   [this broker-id order-id new-size]
    {:pre (> new-size 0)}
    (if-let [broker-orders (@orders broker-id)]
      (let [order (@broker-orders order-id)]
        (swap! order assoc :size new-size))))
  (remove-order-impl [this broker-id order-id]
    (if-let [broker-orders (@orders broker-id)]
      (swap! broker-orders dissoc order-id)))
  (stop-orders-impl [this] nil))

