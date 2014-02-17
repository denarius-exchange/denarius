(ns denarius.tcp
  (:use clojure.core
        lamina.core
        aleph.tcp
        gloss.core
        denarius.order
        denarius.engine)
  (:require [clojure.data.json :as json]))


(def book (ref nil))


(defn inform-match [channel]
  (fn [order-ref-1 order-ref-2 size price]
    (if-not (nil? channel)
      (enqueue channel 
               (json/json-str {:msg-type 1
                               :order-id (:order-id @order-ref-1)
                               :broker-id (:broker-id @order-ref-1)
                               :size size
                               :price price})) )))

(defn handler [channel channel-info]
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
                       [req-type broker-id 
                        order-id
                        order-type-str
                        side-str size 
                        price]             req-params
                       order-type          (case order-type-str "limit" :limit "market" :market)
                       side                (case side-str "bid" :bid "ask" :ask)]
                   (case req-type
                     1 (let [order-ref  (create-order-ref order-id broker-id
                                                          order-type side size price 
                                                          nil [(inform-match channel)])]
                         (insert-order @book order-ref) ))
                   ; return response 
                   (enqueue channel (json/write-str {:msg-type 0 :status :OK})) ))))


(defn start-tcp [order-book port]
  (dosync (ref-set book @order-book))
  (start-tcp-server handler 
                    {:port port
                     :frame (string :utf-8 
                                    :delimiters ["\r\n"])}) )