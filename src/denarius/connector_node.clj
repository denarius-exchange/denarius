(ns denarius.connector-node
  (:use [clojure.tools.logging :only [info]]
        lamina.core
        aleph.tcp
        gloss.core)
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [denarius.connector.db :as db]
            [denarius.connector.db.mockdb])
  (:import [denarius.connector.db.mockdb mockdb]) )


(def default-connector-port 7892)

(def channels (atom {}))

(def connector-options
  [["-h" "--host HOST" "Engine host address"
    :default "localhost"
    :parse-fn identity]
   ["-e" "--engine-port PORT" "Engine port number to connect to"
    :default denarius.engine-node/default-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]])


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
    (if (= 1 msg-type)
      (do
        (if ch-brkr
          (do
            (db/insert broker-id-1 order-id-1 broker-id-2 order-id-2 size price)
            (enqueue ch-brkr (json/write-str {:msg-type 2 :broker-id broker-id-1
                                              :order-id order-id-1 :size size
                                              :price price}) ) ))
        ))))


(defn create-handler [e-host e-port engine-chnl]
  (fn [channel opt]
    (receive-all channel
                 (fn [req]
                   (let [req-params (for [l [:req-type
                                             :broker-id
                                             :order-id
                                             :order-type
                                             :side
                                             :size
                                             :price]] (l (json/read-str req
                                                                        :key-fn keyword) ))
                         [req-type
                          broker-id 
                          order-id
                          order-type-str
                          side-str size 
                          price]             req-params
                         order-type          (case order-type-str "limit" :limit "market" :market)
                         side                (case side-str "bid" :bid "ask" :ask)]
                     (case req-type
                       1 (do 
                           (enqueue engine-chnl req) ))
                     (let [ch-broker (@channels broker-id)]
                       (if ch-broker
                         (do
                           (if-not (= ch-broker channel)
                             (swap! channels #(assoc % broker-id channel)) ))
                         (swap! channels #(assoc % broker-id channel))))
                     ; return response
                     (enqueue channel (json/write-str {:msg-type 0 
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
        e-chnl (create-back-channel e-host e-port)]
    (reset! db/dbname (denarius.connector.db.mockdb/mockdb.))
    (start-front-server port e-host e-port e-chnl) ))