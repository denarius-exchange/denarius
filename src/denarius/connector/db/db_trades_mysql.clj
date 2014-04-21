(ns denarius.connector.db.db-trades-mysql
  (:require [denarius.connector.db-trades :as db]
            [clojure.java.jdbc :as j]))

(deftype db-trades-mysql [^{:volatile-mutable true} dbopt]
  db/db-trades
  (init-trades-impl [this]
    (let [subname  (str "//" (:host dbopt) ":" (:port dbopt) "/" (:dbname dbopt))
          mysql-db {:subprotocol "mysql"
                    :subname subname
                    :user (:user dbopt)
                    :password (:passw dbopt)}]
      (set! dbopt (assoc dbopt :mysql-db mysql-db))))
  (insert-trade-impl [this broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
    (let [mysql-db (:mysql-db dbopt)]
      (j/insert! mysql-db :trades
                 {:brokerid1 broker-id-1 :orderid1 order-id-1
                  :brokerid2 broker-id-2 :orderid2 order-id-2
                  :size size :price price})))
  (query-trades-impl [this broker-id]
    (let [mysql-db (:mysql-db dbopt)
          trades   (j/query mysql-db ["select * from trades"])]
      (map #(-> % (assoc :broker-id-1 (:brokerid1 %)
                                      :order-id-1  (:orderid1 %)
                                      :broker-id-2 (:brokerid2 %)
                                      :order-id-2  (:orderid2 %))
                (dissoc :brokerid1 :brokerid2 :orderid1 :orderid2)) trades)))
  (stop-trades-impl [this] nil) )
