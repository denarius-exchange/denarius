(ns denarius.connector-node
  (:use [clojure.tools.logging :only [info]]
        lamina.core
        aleph.tcp
        gloss.core
        [carica.core])
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [denarius.connector.db :as db]
            [denarius.net.tcp :as tcp])
  (:import [denarius.connector.db nildb]) )


(def channels (atom {}))
(def orders (atom {}))

(def connector-options
  [["-h" "--host HOST" "Engine host address"
    :default "localhost"
    :parse-fn identity]
   ["-e" "--engine-port PORT" "Engine port number to connect to"
    :default tcp/default-engine-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-d" "--database-class CLASSNAME" "Full path of the database driver class to use. Must be present in the classpath."
    :default "denarius.connector.db.nildb"
    :validate [#(not (empty? %)) "Must be not empty"]]])


(defn build-position [data]
  (let [order-map   (json/read-str data :key-fn keyword)
        msg-type    (:msg-type    order-map)
        broker-id-1 (:broker-id-1 order-map)
        order-id-1  (:order-id-1  order-map)
        broker-id-2 (:broker-id-2 order-map)
        order-id-2  (:order-id-2  order-map)
        size        (:size        order-map)
        price       (:price       order-map)
        ch-brkr     (@channels broker-id-1)]
    (condp msg-type
           tcp/message-response-executed
      (do
        (if ch-brkr
          (do
            (db/insert-trade broker-id-1 order-id-1 broker-id-2 order-id-2 size price)
            (enqueue ch-brkr (json/write-str {:msg-type tcp/message-response-executed
                                              :broker-id broker-id-1
                                              :order-id order-id-1 :size size
                                              :price price}) ) ))
        ))))


(defn create-handler [e-host e-port engine-chnl]
  (fn [channel opt]
    (receive-all channel
                 (fn [req]
                   (let [req-map   (json/read-str req :key-fn keyword)
                         req-type  (:req-type req-map)
                         broker-id (:broker-id req-map)]
                     (condp = req-type
                       tcp/message-request-order (let [req-params (for [l [:order-id
                                                                           :order-type
                                                                           :side
                                                                           :size
                                                                           :price]] (l req-map))
                                                       [order-id
                                                        order-type-str
                                                        side-str size
                                                        price]             req-params
                                                       order-type          (case order-type-str "limit" :limit "market" :market)
                                                       side                (case side-str "bid" :bid "ask" :ask)]
                                                   (let [order-data {:size size :price price}]
                                                     (if-let [broker-orders (@orders broker-id)]
                                                       (if-let [order-with-id (@broker-orders order-id)]
                                                         (println "Already sent")
                                                         (do
                                                           (swap! broker-orders assoc order-id order-data)
                                                           (enqueue engine-chnl req)))
                                                       (do
                                                         (enqueue engine-chnl req)
                                                         (swap! orders assoc broker-id (atom {order-id order-data})))))
                                                   )
                       tcp/message-request-list   (if-let [broker-orders (@orders broker-id)]
                                                    (do
                                                      (enqueue channel
                                                               (json/write-str {:msg-type
                                                                                        tcp/message-response-list
                                                                                :orders @broker-orders} ))))
                       tcp/message-request-trades (if-let [broker-trades (db/query-trades broker-id)]
                                                    (let [trades (map (fn [t] (into {} (map (fn [k] {k (k t)})
                                                                                            [:order-id-1 :size :price])))
                                                                      broker-trades)]
                                                      (enqueue channel
                                                               (json/write-str {:msg-type
                                                                                        tcp/message-response-trades
                                                                                :trades trades} ))))
                       )
                     (let [ch-broker (@channels broker-id)]
                       (if ch-broker
                         (do
                           (if-not (= ch-broker channel)
                             (swap! channels #(assoc % broker-id channel)) ))
                         (swap! channels #(assoc % broker-id channel))))
                     ; return response
                     (enqueue channel (json/write-str {:msg-type tcp/message-response-received
                                                       :status :OK}) )))))  )


(defn start-front-server [port e-host e-port e-chnl]
  (info "Starting connector front server on port" port)
  (start-tcp-server (create-handler e-host e-port e-chnl)
                    {:port port
                     :frame (string :utf-8
                                    :delimiters ["\r\n"])}) )

(defn create-back-channel [e-host e-port]
  (info "Creating connector back channel to engine" e-host " port" e-port)
  (let [tcp-opt     {:host e-host
                     :port e-port
                     :frame (string :utf-8 :delimiters ["\r\n"])}
        engine-chnl (wait-for-result (tcp-client tcp-opt))]
    (receive-all engine-chnl build-position)
    engine-chnl))


(defn start-connector [prog-opt args]
  (info "Starting connector node")
  (let [{:keys [options arguments errors summary]} (parse-opts args
                                                               connector-options)
        port   (:port prog-opt)
        e-port (:engine-port options)
        e-host (:host options)
        dbopt  (:database-class options)
        dbpath (if (= dbopt "denarius.connector.db.nildb")
                 (config :connector :database-class) dbopt)
        dbsplt (clojure.string/split dbpath #"\.+")
        dbname (last dbsplt)
        dbpspl (keep-indexed (fn [i x] (if (< (+ i 1) (count dbsplt)) x)) dbsplt)
        dbpkg  (clojure.string/join "." dbpspl)
        e-chnl (create-back-channel e-host e-port)]
    ; Set the driver class for the database system to use it
    (require (eval `(symbol ~dbpkg)))
    (import [(eval `(symbol dbpkg) `(symbol dbname))])
    (reset! db/dbname (eval `(new ~(symbol dbpath))))
    (start-front-server port e-host e-port e-chnl) ))