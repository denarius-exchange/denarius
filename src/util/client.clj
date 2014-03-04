(ns util.client
  (:use lamina.core
        gloss.core
        aleph.tcp)
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.data.json :as json])
  (:gen-class))


(def position (ref 0))
(def orders (ref []))

(def read-options
  [["-a" "--ask" "Order is ask"
    :default false]
   ["-b" "--bid" "Order is bid"
    :default true]
   ["-m" "--market" "Order is marked to market"
    :default false]
   ["-l" "--limit" "Order is limit"
    :default true]
   ["-p" "--price PRICE" "Price"
    :id :price
    :parse-fn #(Integer/parseInt %)
    :default 10
    ;:assoc-fn (fn [m k _] (update-in m [k] inc))
    ]
   ["-s" "--size SIZE" "Size"
    :id :size
    :parse-fn #(Integer/parseInt %)
    :default 1
    ]
   ])

(def program-options
  ;; An option with a required argument
  [["-p" "--port PORT" "Port number"
    :default 7891
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ["-h" "--host HOST" "Host, either host name or IP address"
    :default "localhost"]
   ["-i" "--id ID" "Broker ID. Numeric."
    :default 1
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65536"]]
   ;; A boolean option defaulting to nil
   ["-?" "--help" "Show help"]])


(defmulti show-commands identity :default nil)

(defmethod show-commands nil [command]
  (print " ") ; space formatting
  (println "send\t\tSend an order to the server\n"
           "position\tShow net position\n"
           "help\t\tShow this help\n"
           "history\tShow sent order history\n"
           "exit\t\tQuit program (also quit)\n") )

(defmethod show-commands "send" [command]
  (println "\tSend order to the server\n"
           "\tOptions:\n"
           "\t-l\t\tLimit order\n"
           "\t-m\t\tMarket order\n"
           "\t-b\t\tBid order\n"
           "\t-a\t\tAsk order\n"
           "\t-s SIZE\t\tOrder size\n"
           "\t-p PRICE\tOrder price\n"))
  
(defn print-order [order-id order-type side size price]
  (println "Sending" (if (= :market order-type) "MARKET" "LIMIT")
           "order," (if (= :ask side) "SELLING" "BUYING")
           size  "units" (str (if (= :market order-type) "" (str "at price " price)))
           (str ", with ID=" order-id)) )

(defn print-response [order-id side price]
  (println "Your " (if (= :ask side) "SELLING" "BUYING") (str "order with ID=" order-id)
           "has been executed at price" price))

(defn print-history [orders]
  (loop [order-list orders]
    (if-not (empty? order-list)
      (let [order      (first order-list)
            broker-id  (:broker-id order)
            order-id   (:order-id order)
            order-type (:order-type order)
            side       (:side order)
            size       (:size order)]            
        (println "Broker ID=" broker-id " Order ID=" order-id
                 " Type=" order-type " Side=" side " Size=" size)
        (recur (rest order-list)) ))))

(defn build-position [data]
  (let [order-map (json/read-str data :key-fn keyword)
        msg-type  (:msg-type order-map)
        order-id  (:order-id order-map)
        size      (:size order-map)
        price     (:price order-map)]
    (if (= 2 msg-type)
      (let [side (first (keep #(if (= order-id (:order-id %)) (:side %)) @orders))]
        (dosync (alter position (if (= :ask side) #(- % size) (partial + size))))
        (print-response order-id side price) ))))


(defn send-order [channel broker-id order-id opt]
  (let [side       (if (:ask opt) :ask :bid)
        order-type (if (:market opt) :market :limit)
        price      (:price opt)
        size       (:size opt)
        order-map  {:req-type 1 :broker-id broker-id :order-id order-id
                    :order-type order-type :side side :size size :price price}
        order-str  (json/write-str order-map)]
    (print-order order-id order-type side size price)
    (dosync (alter orders conj order-map))
    (enqueue channel order-str) ))

(defn exit? [x] (or (= "exit" x)
                    (= "quit" x)))

(defn input [channel broker-id]
  (loop [order-id 1]
    (print "> ")
    (flush)
    (let [v (read-line)]
      (if (exit? v)
        (do
          (close channel)
          (println "Bye!"))
        (let [line    (.split v " ")
              command (first line)
              body    (rest line)
              params  (parse-opts body read-options)
              opt     (:options params)]
          (case command
            "help"     (show-commands (second line))
            "send"     (send-order channel broker-id order-id opt)
            "position" (println "CURRENT NET POSITION: " @position)
            "history"  (print-history @orders)
            ""         nil
            (println command "is not a command. See help,"))
          (Thread/sleep 200)
          (recur (inc order-id) ))))))


(defn -main [& args]
  (let [params  (parse-opts args program-options)
        options (:options params)
        help    (:help options)
        brid    (:id   options)
        host    (:host options)
        port    (:port options)]
    (if help
      (println (:summary params))
      (let [tcp-opt {:host host
                     :port port
                     :frame (string :utf-8 :delimiters ["\r\n"])}
            channel (wait-for-result (tcp-client tcp-opt))]
        (receive-all channel build-position)
        (println "DENARIUS UTILITY TEST CLIENT\nSee Denarius' Wiki\nUse help for commands")
        (input channel brid)))) )