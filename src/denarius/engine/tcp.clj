(ns denarius.engine.tcp
  (:use clojure.core
        lamina.core
        aleph.tcp
        gloss.core
        denarius.order
        denarius.engine
        denarius.net.tcp)
  (:require [clojure.data.json :as json]
            [clojure.core.async :as async]))


(def book (ref nil))
(def async-channel (ref nil))

(defn inform-match [channel]
  (fn [order-ref-1 order-ref-2 size price]
    (if-not (nil? channel)
      (enqueue channel 
               (json/json-str {:msg-type    message-response-executed
                               :order-id-1  (:order-id  @order-ref-1)
                               :broker-id-1 (:broker-id @order-ref-1)
                               :order-id-2  (:order-id  @order-ref-2)
                               :broker-id-2 (:broker-id @order-ref-2)
                               :size        size
                               :price       price})) )))

(defn inform-cancel [channel]
  (fn [order-ref]
    (if-not (nil? channel)
      (enqueue channel
               (json/json-str {:msg-type  message-response-cancel
                               :broker-id (:broker-id @order-ref)
                               :order-id  (:order-id  @order-ref)})) )))

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
                   (condp = req-type
                     message-request-order (let [order-ref  (create-order-ref order-id broker-id
                                                                              order-type side size price
                                                                              [(inform-cancel channel)]
                                                                              [(inform-match channel)])]
                                             (insert-order @book order-ref)
                                             ; We unlock the matching loop
                                             (async/go (async/>! @async-channel 1)) ; duplicate??
                                             (async/go (async/>! @async-channel 1)) )
                     message-request-cancel (do
                                              (remove-order @book broker-id order-id order-type side price))
                     )
                   ; return response 
                   ; +++++++(enqueue channel (json/write-str {:msg-type 0 :status :OK}))
                   ))))


(defn start-tcp [order-book port async-ch]
  (dosync (ref-set book @order-book))
  (dosync (ref-set async-channel async-ch))
  (start-tcp-server handler 
                    {:port port
                     :frame (string :utf-8 
                                    :delimiters ["\r\n"])}) )