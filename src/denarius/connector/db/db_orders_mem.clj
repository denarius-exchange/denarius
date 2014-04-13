(ns denarius.connector.db.db-orders-mem
  (:require [denarius.connector.db-orders :as db]))

(def orders (atom []))

(deftype db-orders-mem []
  db/db-orders
  (init-orders-impl  [this] (reset! orders {}))
  (insert-order-impl [this broker-id order]
    (let [order-id (:order-id order)]
      (if-let [broker-orders (@orders broker-id)]
        (swap! broker-orders assoc broker-id (atom {order-id (atom order)}))
        (swap! orders assoc broker-id (atom {order-id (atom order)}))
        )))
  (query-orders-impl [this broker-id] nil)
  (alter-size-impl   [this broker-id order-id new-size]
    {:pre (>= new-size 0)}
    (if-let [broker-orders (@orders broker-id)]
      (if (= new-size 0)
        (swap! broker-orders dissoc order-id)
        (let [order (@broker-orders order-id)]
          (swap! order assoc :size new-size)))))
  (remove-order-impl [this broker-id order-id]
    (if-let [broker-orders (@orders broker-id)]
      (swap! broker-orders dissoc order-id)))
  (stop-orders-impl [this] nil))