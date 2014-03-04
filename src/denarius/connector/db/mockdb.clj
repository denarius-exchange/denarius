(ns denarius.connector.db.mockdb
  (:require [denarius.connector.db :as db]))


(def trades (atom []))


(deftype mockdb []
  db/db-proto
  (init-impl [this] nil)
  (insert-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
    (let [newtrade {:broker-id-1 broker-id-1 :order-id-1 order-id-1 
                    :broker-id-2 broker-id-2 :order-id-2 order-id-2 
                    :size size :price price}]
      (swap! trades conj newtrade) ))
  (stop-impl [this] nil) )