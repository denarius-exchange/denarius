(ns denarius.connector.db.db-deskinfo-file
  (:require [denarius.connector.db-deskinfo :as db]
            [clojure.data.json :as json]))

(def deskinfo (atom {}))

(deftype db-trades-file [dbopt]
  db/db-trades
  (init-deskinfo-impl [this]
    (let [info (slurp "resources/db_deskinfo_file.json")]
      (reset! deskinfo (json/read-str info))
      (println @deskinfo)))
  (query-deskinfo-available-funds-impl [this broker-id]
    (if-let [broker-info (@deskinfo broker-id)]
      (let [amount (:amount @broker-info)
            margin (:margin @broker-info)]
        (- amount margin))
      nil))
  (query-deskinfo-cantrade [this broker-id initial]
    (if-let [available (query-deskinfo-available-funds-impl)]
      (> available 0)
      false))
  (stop-trades-impl [this] nil) )





