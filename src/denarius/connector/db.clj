(ns denarius.connector.db)

; We load the implementations from here
;(load "cassandra")

; Database name to be set via configuration
(def dbname (ref nil))


(defn dbname-dispatch [dbname & _] dbname)

(defmulti init-impl dbname-dispatch)

(defmethod init-impl nil [dbname] nil)

(defmulti insert-impl dbname-dispatch)

(defmethod insert-impl nil [dbname broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
  nil )


(defmulti stop-impl dbname-dispatch)

(defmethod stop-impl nil [dbname] nil)



; Methods exposed by 
(defn init [] (init-impl @dbname) )

(defn insert [broker-id-1 order-id-1 broker-id-2 order-id-2 size price]
  (insert-impl @dbname broker-id-1 order-id-1 broker-id-2 order-id-2 size price) )

(defn stop [] (stop-impl @dbname))