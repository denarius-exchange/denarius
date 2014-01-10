(ns denarius.core-test
  (:use clojure.core
        clojure.test
        gloss.core
        lamina.core
        aleph.tcp
        denarius.order
        denarius.core
        denarius.engine)
  (:require clojure.tools.logging
            [clojure.data.json :as json]
            [org.httpkit.client :as http]
            denarius.tcp))

(deftest test-core
  (let [order-id-1 1
        order-id-2 2
        order-id-3 3
        order-id-4 4
        broker-id  1
        price      10
        size       1
        result-bid (ref nil)
        result-ask (ref nil)
        last-price (ref nil)
        asset-name "EURUSD"
        options    {:host "localhost",
                    :port 8080,
                    :frame (string :utf-8 :delimiters ["\r\n"])}
        port        denarius.tcp/port
        idle-time   1000]
    (testing "Two limit orders sent to the TCP server. Check that they are matched after some time"
             (clear-book @book)
             (let [req-ask     (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-1 
                                                :order-type :limit :side :ask :size 1 :price 10})
                   req-bid     (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-2
                                                :order-type :limit :side :bid :size 1 :price 10})
                   stop-server (denarius.tcp/start-tcp book)
                   channel     (wait-for-result (tcp-client options))]
               (start-matching-loop)
               (enqueue channel req-ask)
               (enqueue channel req-bid)
               (Thread/sleep idle-time)
               (is (= 0 (market-depth @book :bid price)))
               (is (= 0 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Four limit orders sent to the TCP server. Check that they are matched after some time"
             (clear-book @book)
             (let [req-ask-1   (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-1
                                                :order-type :limit :side :ask :size 1 :price 10})
                   req-ask-2   (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-2
                                                :order-type :limit :side :ask :size 2 :price 10})
                   req-bid-1   (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-3
                                               :order-type :limit  :side :bid :size 2 :price 10})
                   req-bid-2   (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-4
                                                :order-type :limit :side :bid :size 2 :price 10})
                   stop-server (denarius.tcp/start-tcp book)
                   channel     (wait-for-result (tcp-client options))]
               (start-matching-loop)
               (enqueue channel req-ask-1)
               (enqueue channel req-bid-1)
               (enqueue channel req-ask-2)
               (enqueue channel req-bid-2)
               (Thread/sleep idle-time)
               (is (= 1 (market-depth @book :bid price)))
               (is (= 0 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Two limit orders sent to the TCP server (2 to 1). Check that they are partially 
              matched after some time"
             (clear-book @book)
             (let [req-ask     (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-1
                                                :order-type :limit :side :ask :size 2 :price 10})
                   req-bid     (json/write-str {:req-type 1 :broker-id 1 :order-id order-id-2
                                                :order-type :limit :side :bid :size 1 :price 10})
                   stop-server (denarius.tcp/start-tcp book)
                   channel     (wait-for-result (tcp-client options))]
               (start-matching-loop)
               (enqueue channel req-ask)
               (enqueue channel req-bid)
               (Thread/sleep idle-time)
               (is (= 0 (market-depth @book :bid price)))
               (is (= 1 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Bulk test: Send 1000 random-side limit orders and check matching"
             (clear-book @book)
             (let [stop-server   (denarius.tcp/start-tcp book)
                   channel       (wait-for-result (tcp-client options))
                   max-requests  1000
                   send-function (fn []
                                   (loop [side      (if-not (= (rand-int 2) 0) :bid :ask)
                                          requests  0
                                          total-bid 0
                                          total-ask 0]
                                     (if (>= requests max-requests)
                                       {:ask total-ask :bid total-bid}
                                       (let [size      (inc (rand-int 10))
                                             req-order (json/write-str {:req-type 1 :broker-id 1 :order-id (get-order-id)
                                                                        :order-type :limit :side side :size size :price 10})]
                                         (dosync
                                           ; Asynchronously send requests (orders), the future by
                                           ; http/post must be realized in order to stop the server
                                           ; without exceptions, so we need extra threads (agents)
                                           ; to send requests
                                           (future (enqueue channel req-order) ))
                                         (recur (if-not (= (rand-int 2) 0) :bid :ask)
                                                (inc requests)
                                                (if (= side :bid)
                                                  (+ total-bid size)
                                                  total-bid)
                                                (if (= side :ask)
                                                  (+ total-ask size)
                                                  total-ask))
                                         ))))
                   total         (future (send-function))]
               (start-matching-loop)
               (Thread/sleep (* 3 idle-time))
               (time 
                 (do (let [ask-total (:ask @total)
                           bid-total (:bid @total)]
                       (is (= (max 0 (- bid-total ask-total)) (market-depth @book :bid price)))
                       (is (= (max 0 (- ask-total bid-total)) (market-depth @book :ask price))) )
                   ))
               (stop-server)
               ))
    (testing "Bulk test: Send 1000 random-side limit AND market orders and check matching"
             (clear-book @book)
             (let [stop-server   (denarius.tcp/start-tcp book)
                   channel       (wait-for-result (tcp-client options))
                   max-requests  1000
                   max-size      10
                   send-function (fn []
                                   (loop [side      (if-not (= (rand-int 2) 0) :bid :ask)
                                          requests  0
                                          total-bid 0
                                          total-ask 0]
                                     (if (>= requests max-requests)
                                       {:ask total-ask :bid total-bid}
                                       (let [order-type (if (= (rand-int 3) 0) :market :limit) 
                                             price      10
                                             size       (inc (rand-int max-size))
                                             req-order  (json/write-str {:req-type 1 :broker-id 1 :order-id (get-order-id)
                                                                         :order-type order-type :side side :size size
                                                                         :price price})]
                                         (dosync
                                           ; Asynchronously send requests (orders), the future by
                                           ; http/post must be realized in order to stop the server
                                           ; without exceptions, so we need extra threads (agents)
                                           ; to send requests
                                           (future (enqueue channel req-order) ))
                                         (recur (if-not (= (rand-int 2) 0) :bid :ask)
                                                (inc requests)
                                                (if (= side :bid)
                                                  (+ total-bid size)
                                                  total-bid)
                                                (if (= side :ask)
                                                  (+ total-ask size)
                                                  total-ask))
                                         ))))
                   total         (future (send-function))]
               (start-matching-loop)
               (Thread/sleep (* 3 idle-time))
               (time 
                 (do 
                   (let [ask-total   (:ask @total)
                         bid-total   (:bid @total)
                         mktdpth-ask (market-depth @book :ask price)
                         mktdpth-bid (market-depth @book :bid price)]
                     (is (= 0 (min mktdpth-ask mktdpth-bid)) )
                     (is (= (max (- bid-total ask-total) (- ask-total bid-total))
                            (max (+ mktdpth-ask (size-market-orders @book :ask))
                                 (+ mktdpth-bid (size-market-orders @book :bid)) )))
                     (is (if (= 0 mktdpth-ask) (< (count @(:market-ask @book)) max-size) true))
                     (is (if (= 0 mktdpth-bid) (< (count @(:market-bid @book)) max-size) true))
                     )))
               (stop-server)
               ))
    (testing "Bulk test: Send 1000 random-side, randomly priced limit AND market orders and check
              the best bid order is cheaper than the best ask order"
             (clear-book @book)
             (let [stop-server   (denarius.tcp/start-tcp book)
                   channel       (wait-for-result (tcp-client options))
                   max-requests  1000
                   send-function (fn []
                                   (loop [side      (if-not (= (rand-int 2) 0) :bid :ask)
                                          requests  0
                                          total-bid 0
                                          total-ask 0]
                                     (if (>= requests max-requests)
                                       {:ask total-ask :bid total-bid}
                                       (let [order-type    (if (= (rand-int 5) 0) :market :limit) 
                                             price         (+ (rand-int 10) (if (= size :bid) 5 10))
                                             size          (inc (rand-int 10))
                                             req-order  (json/write-str {:req-type 1 :broker-id 1 :order-id (get-order-id)
                                                                         :order-type order-type :side side 
                                                                         :size size :price price})]
                                         (dosync
                                           ; Asynchronously send requests (orders), the future by
                                           ; http/post must be realized in order to stop the server
                                           ; without exceptions, so we need extra threads (agents)
                                           ; to send requests
                                           (future (enqueue channel req-order) ))
                                         (recur (if-not (= (rand-int 2) 0) :bid :ask)
                                                (inc requests)
                                                (if (= side :bid)
                                                  (+ total-bid size)
                                                  total-bid)
                                                (if (= side :ask)
                                                  (+ total-ask size)
                                                  total-ask))
                                         ))))
                   total         (future (send-function))]
               (start-matching-loop)
               (Thread/sleep idle-time)
               (time 
                 (do 
                   (let [ask-total   (:ask @total)
                         bid-total   (:bid @total)
                         mktdpth-ask (market-depth @book :ask price)
                         mktdpth-bid (market-depth @book :bid price)]
                     (is (= 0 (min mktdpth-ask mktdpth-bid)) )
                     (is (if (= 0 mktdpth-ask) (= (count @(:market-ask @book)) 0) true))
                     (is (if (= 0 mktdpth-bid) (= (count @(:market-bid @book)) 0) true))
                     (is (< (:price @(first @(best-price-level-ref @book :bid)))
                            (:price @(first @(best-price-level-ref @book :ask)))))
                     )))
               (stop-server)
               ))
    ))
