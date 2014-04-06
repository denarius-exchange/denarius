(ns denarius.tcp-test
  (:use clojure.test
        gloss.core
        lamina.core
        aleph.tcp
        denarius.order
        denarius.engine
        denarius.tcp)
  (:require [clojure.data.json :as json]
            [clojure.core.async :as async]
             [denarius.net.tcp :as tcp]) )


(deftest test-tcp
  (let [order-id-1 1
        order-id-2 2
        order-id-3 3
        order-id-4 4
        broker-id  1
        type       :market
        type-lmt   :limit
        price      10
        size       1
        result-bid (ref nil)
        result-ask (ref nil)
        last-price (ref nil)
        cross      (fn [order-in-book order-incoming size price]
                     (dosync (ref-set last-price [price size])))
        options    {:host "localhost",
                    :port tcp/default-engine-port,
                    :frame (string :utf-8 :delimiters ["\r\n"])}
        asset-name "EURUSD"
        port       tcp/default-engine-port]
    (testing "Two limit orders sent to the HTTP server. Check if they exist on the book"
             (let [order-book     (ref (create-order-book asset-name))
                   matching-agent (agent 1)
                   req-ask        (json/write-str {:req-type tcp/message-request-order
                                                   :broker-id 1 :order-id 1 :order-type :limit 
                                                   :side :ask :size 2 :price 10})
                   req-bid        (json/write-str {:req-type tcp/message-request-order :broker-id 1
                                                   :order-id 2 :order-type :limit 
                                                   :side :bid :size 3 :price 10})
                   async-ch   (async/chan)
                   stop-tcp   (start-tcp order-book port async-ch)
                   channel    (wait-for-result (tcp-client options))]
               (enqueue channel req-ask)
               (enqueue channel req-bid)
               (Thread/sleep 500)
               (is (= 3 (market-depth @order-book :bid price)))
               (is (= 2 (market-depth @order-book :ask price)))
               (stop-tcp) )) ))