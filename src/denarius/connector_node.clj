(ns denarius.connector-node
  (:use [clojure.tools.logging :only [info]]
        lamina.core
        aleph.tcp
        gloss.core
        [carica.core])
  (:require [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json]
            [denarius.order :as o]
            [denarius.connector.db-trades :as db-trades]
            [denarius.connector.db-orders :as db-orders]
            [denarius.connector.db-deskinfo :as db-deskinfo]
            [denarius.net.tcp :as tcp]))


(def channels (atom {}))

(def connector-options
  [["-h" "--host HOST" "Engine host address"
    :default "localhost"
    :parse-fn identity]
   ["-e" "--engine-port PORT" "Engine port number to connect to"
    :default tcp/default-engine-port
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-t" "--database-trades-class CLASSNAME" "Full path of the database driver class to use with trades database. Must be present in the classpath."
    :default "denarius.connector.db.db-trades-nil"
    :validate [#(not (empty? %)) "Must be not empty"]]
   ["-o" "--database-orders-class CLASSNAME" "Full path of the database driver class to use with orders database. Must be present in the classpath."
    :default "denarius.connector.db.db-orders-nil"
    :validate [#(not (empty? %)) "Must be not empty"]]
   ["-b" "--database-deskinfo-class CLASSNAME" "Full path of the database driver class to use with deskinfo database. Must be present in the classpath."
    :default "denarius.connector.db.db-deskinfo-nil"
    :validate [#(not (empty? %)) "Must be not empty"]]])


(defn build-position [data]
  (let [order-map   (json/read-str data :key-fn keyword)
        msg-type    (:msg-type order-map)]
    (println order-map msg-type)
    (condp = msg-type
      tcp/message-response-executed (let [broker-id-1 (:broker-id-1 order-map)
                                          order-id-1  (:order-id-1  order-map)
                                          broker-id-2 (:broker-id-2 order-map)
                                          order-id-2  (:order-id-2  order-map)
                                          size        (:size        order-map)
                                          price       (:price       order-map)]
                                      (if-let [ch-brkr (@channels broker-id-1)]
                                        (do
                                          (db-trades/insert-trade broker-id-1 order-id-1 broker-id-2 order-id-2 size price)
                                          (db-orders/decrease-size broker-id-1 order-id-1 size)
                                          (enqueue ch-brkr (json/write-str {:msg-type tcp/message-response-executed
                                                                            :broker-id broker-id-1
                                                                            :order-id order-id-1 :size size
                                                                            :price price}) ))
                                        ))
      tcp/message-response-cancel   (let [broker-id (:broker-id order-map)
                                          order-id  (:order-id  order-map)]
                                      (if-let [ch-brkr (@channels broker-id)]
                                        (do
                                          (println "CANCELBACK!")
                                          (db-orders/remove-order broker-id order-id)
                                          (enqueue ch-brkr (json/write-str {:msg-type tcp/message-response-cancel
                                                                            :order-id order-id}) ))
                                        )))))


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
                                                       side                (case side-str "bid" :bid "ask" :ask)
                                                       order               (o/create-order order-id broker-id order-type side size price)]
                                                   (if-let [order-with-id (db-orders/query-order broker-id order-id)]
                                                     (println "Already sent")  ; Remove when necessary
                                                     (if (db-deskinfo/query-deskinfo-cantrade broker-id size)
                                                       (do
                                                         (db-orders/insert-order order)
                                                         (enqueue engine-chnl req)))))
                       tcp/message-request-list   (if-let [broker-orders (db-orders/query-orders broker-id)]
                                                    (let [order-list (map deref (vals broker-orders))]
                                                      (enqueue channel
                                                              (json/write-str {:msg-type tcp/message-response-list
                                                                               :orders order-list} ))))
                       tcp/message-request-trades (if-let [broker-trades (db-trades/query-trades broker-id)]
                                                    (let [fn-idkey-tran #(condp = % :order-id-1 :order-id %)
                                                          trades (map (fn [t] (into {} (map (fn [k] {(fn-idkey-tran k) (k t)})
                                                                                            [:order-id-1 :size :price])))
                                                                      broker-trades)]
                                                      (enqueue channel
                                                               (json/write-str {:msg-type tcp/message-response-trades
                                                                                :trades trades} ))))
                       tcp/message-request-cancel (if-let [broker-orders (db-orders/query-orders broker-id)]
                                                    (if-let [order       (broker-orders (:order-id req-map))]
                                                      (let [order-id   (:order-id req-map)
                                                            order-type (:type  @order)
                                                            side       (:side  @order)
                                                            price      (:price @order)
                                                            req        {:req-type tcp/message-request-cancel
                                                                        :broker-id broker-id
                                                                        :order-id order-id
                                                                        :order-type (condp = order-type
                                                                                      :market "market"
                                                                                      :limit "limit")
                                                                        :side side
                                                                        :price price}
                                                            req-str (json/write-str req)]
                                                        (println "CANCEL" req)
                                                        (enqueue engine-chnl req-str))))
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
        db-trd (:database-trades-class options)
        db-ord (:database-orders-class options)
        db-nfo (:database-deskinfo-class options)
        dbtpth (if (= db-trd "denarius.connector.db.db-trades-nil")
                 (config :connector :database-trades :class) db-trd)
        dbopth (if (= db-ord "denarius.connector.db.db-orders-nil")
                 (config :connector :database-orders :class) db-ord)
        dbbpth (if (= db-nfo "denarius.connector.db.db-deskinfo-nil")
                 (config :connector :database-deskinfo :class) db-nfo)
        dbtopt (config :connector :database-trades :options)
        dboopt (config :connector :database-orders :options)
        dbbopt (config :connector :database-deskinfo :options)
        dbsplt (clojure.string/split dbtpth #"\.+")
        dboplt (clojure.string/split dbopth #"\.+")
        dbbplt (clojure.string/split dbbpth #"\.+")
        dbtnme (last dbsplt)
        dbonme (last dboplt)
        dbbnme (last dbbplt)
        dbtsp2 (keep-indexed (fn [i x] (if (< (+ i 1) (count dbsplt)) x)) dbsplt)
        dbosp2 (keep-indexed (fn [i x] (if (< (+ i 1) (count dboplt)) x)) dboplt)
        dbbsp2 (keep-indexed (fn [i x] (if (< (+ i 1) (count dbbplt)) x)) dbbplt)
        dbtpkg (clojure.string/join "." dbtsp2)
        dbopkg (clojure.string/join "." dbosp2)
        dbbpkg (clojure.string/join "." dbbsp2)
        e-chnl (create-back-channel e-host e-port)]
    ; Set the driver class for the database system (trades) to use
    (require (eval `(symbol ~dbtpkg)))
    (import [(eval `(symbol dbtpkg) `(symbol dbtnme))])
    (reset! db-trades/dbname (eval `(new ~(symbol dbtpth) ~dbtopt)))
    (db-trades/init-trades)
    ; Set the driver class for the database system (orders) to use
    (require (eval `(symbol ~dbopkg)))
    (import [(eval `(symbol dbopkg) `(symbol dbonme dboopt))])
    (reset! db-orders/dbname (eval `(new ~(symbol dbopth) ~dboopt)))
    (db-orders/init-orders)
    ; Set the driver class for the database system (deskinfo) to use
    (require (eval `(symbol ~dbbpkg)))
    (import [(eval `(symbol dbbpkg) `(symbol dbbnme dbbopt))])
    (reset! db-deskinfo/dbname (eval `(new ~(symbol dbbpth) ~dbbopt)))
    (db-deskinfo/init-deskinfo)
    ; Create the server
    (start-front-server port e-host e-port e-chnl) ))