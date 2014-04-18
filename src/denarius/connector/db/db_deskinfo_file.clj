(ns denarius.connector.db.db-deskinfo-file
  (:require [denarius.connector.db-deskinfo :as db]
            [clojure.data.json :as json]))

(def deskinfo (atom {}))

(deftype db-deskinfo-file [dbopt]
  db/db-deskinfo
  (init-deskinfo-impl [this]
    (let [json-info (slurp "resources/db_deskinfo_file.json")
          json-info (json/read-str json-info)
          map-info  (into {} (for [[k v] json-info]
                               [(Long/parseLong k) (into {} (map (fn [ki vi] (try [(keyword ki) (Long/parseLong vi)]
                                                                                  (catch Exception e [(keyword ki) vi]))) (keys v) (vals v)))]))]
      (reset! deskinfo map-info)))
  (query-deskinfo-available-funds-impl [this broker-id]
    (if-let [broker-info (@deskinfo broker-id )]
      (let [amount (:amount broker-info)
            margin (:margin broker-info)]
        (- amount margin))
      nil))
  (query-deskinfo-cantrade-impl [this broker-id initial]
    (if-let [available (db/query-deskinfo-available-funds broker-id)]
      (> available 0)
      false))
  (stop-deskinfo-impl [this] nil) )





