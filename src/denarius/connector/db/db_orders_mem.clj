(ns denarius.connector.db.db-orders-mem
  (:require [denarius.connector.db-orders :as db]))

(def orders (atom {}))

(deftype db-orders-mem []
  db/db-orders
  (init-orders-impl  [this] (reset! orders {}))
  (insert-order-impl [this order]
    (let [broker-id (:broker-id order)
          order-id (:order-id order)]
      (if-let [broker-orders (@orders broker-id)]
        (swap! broker-orders assoc broker-id (atom {order-id (atom order)}))
        (swap! orders assoc broker-id (atom {order-id (atom order)}))
        )))
  (query-orders-impl [this broker-id] (@orders broker-id))
  (query-order-impl [this broker-id order-id]
    (let [broker-oders (@orders broker-id)
          order        (@broker-oders order-id)]
      @order))
  (alter-size-impl   [this broker-id order-id new-size]
    {:pre (>= new-size 0)}
    (if-let [broker-orders (@orders broker-id)]
      (if (= new-size 0)
        (swap! broker-orders dissoc order-id)
        (let [order (@broker-orders order-id)]
          (swap! order assoc :size new-size)))))
  (decrease-size-impl [this broker-id order-id amount]
    (let [order        (db/query-order-impl this broker-id order-id)
          current-size (:size order)
          new-size     (- current-size amount)]
      (db/alter-size-impl this broker-id order-id new-size)))
  (remove-order-impl [this broker-id order-id]
    (if-let [broker-orders (@orders broker-id)]
      (swap! broker-orders dissoc order-id)))
  (stop-orders-impl [this] nil))