(ns denarius.core-test
  (:use clojure.core
        clojure.test
        clojure.tools.logging
        denarius.order
        denarius.core
        denarius.engine)
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            denarius.http))

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
        options    {:timeout 200             ; ms
                    :basic-auth ["user" "pass"]
                    :user-agent "User-Agent-string"
                    :headers {"X-Header" "Value"}}
        port        denarius.http/port
        idle-time   2000]
    (testing "Two limit orders sent to the HTTP server. Check that they are matched after some time"
             (clear-book @book)
             (let [options-ask    (json/write-str {:broker-id 1 :side :ask :size 1 :price 10})
                   options-bid    (json/write-str {:broker-id 1 :side :bid :size 1 :price 10})
                   stop-server    (denarius.http/start-http book)]
               (start-matching-loop)
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-ask}))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-bid}))
               (Thread/sleep idle-time)
               (is (= 0 (market-depth @book :bid price)))
               (is (= 0 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Four limit orders sent to the HTTP server. Check that they are matched after some time"
             (clear-book @book)
             (let [options-ask-1  (json/write-str {:broker-id 1 :side :ask :size 1 :price 10})
                   options-ask-2  (json/write-str {:broker-id 1 :side :ask :size 2 :price 10})
                   options-bid-1  (json/write-str {:broker-id 1 :side :bid :size 2 :price 10})
                   options-bid-2  (json/write-str {:broker-id 1 :side :bid :size 2 :price 10})
                   stop-server    (denarius.http/start-http book)]
               (start-matching-loop)
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-ask-1}))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-bid-1}))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-bid-2}))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-ask-2}))
               (Thread/sleep idle-time)
               (is (= 1 (market-depth @book :bid price)))
               (is (= 0 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Two limit orders sent to the HTTP server (2 to 1). Check that they are partially 
              matched after some time"
             (clear-book @book)
             (let [options-ask    (json/write-str {:broker-id 1 :side :ask :size 2 :price 10})
                   options-bid    (json/write-str {:broker-id 1 :side :bid :size 1 :price 10})
                   stop-server    (denarius.http/start-http book)]
               (start-matching-loop)
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-ask}))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params {:order options-bid}))
               (Thread/sleep idle-time)
               (is (= 0 (market-depth @book :bid price)))
               (is (= 1 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Bulk test: Send 1000 random-side limit orders and check matching"
             (clear-book @book)
             (let [stop-server   (denarius.http/start-http book)
                   max-requests  1000
                   send-function (fn []
                                   (loop [side      (if-not (= (rand-int 2) 0) :bid :ask)
                                          requests  0
                                          total-bid 0
                                          total-ask 0]
                                     (if (>= requests max-requests)
                                       {:ask total-ask :bid total-bid}
                                       (let [size          (inc (rand-int 10))
                                             options-order (json/write-str {:broker-id 1 :side side 
                                                                            :size size :price 10})
                                             agent         (agent 0)]
                                         (dosync
                                           ; Asynchronously send requests (orders), the future by
                                           ; http/post must be realized in order to stop the server
                                           ; without exceptions, so we need extra threads (agents)
                                           ; to send requests
                                           (send-off agent
                                                     @(http/post (str "http://localhost:" port "/order-new-limit")
                                                                 (assoc options :query-params {:order options-order}) )
                                                     ))
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
               (time 
                 (do (start-matching-loop)
                     (let [ask-total (:ask @total)
                           bid-total (:bid @total)]
                        (is (= (max 0 (- bid-total ask-total)) (market-depth @book :bid price)))
                        (is (= (max 0 (- ask-total bid-total)) (market-depth @book :ask price))) )
                     ))
               (Thread/sleep idle-time)
               (stop-server)
               ))
    (testing "Bulk test: Send 1000 random-side limit AND market orders and check matching"
             (clear-book @book)
             (let [stop-server   (denarius.http/start-http book)
                   max-requests  1000
                   max-size      10
                   send-function (fn []
                                   (loop [side      (if-not (= (rand-int 2) 0) :bid :ask)
                                          requests  0
                                          total-bid 0
                                          total-ask 0]
                                     (if (>= requests max-requests)
                                       {:ask total-ask :bid total-bid}
                                       (let [order-type    (if (= (rand-int 3) 0) :market :limit) 
                                             price         10
                                             size          (inc (rand-int max-size))
                                             options-order (json/write-str {:broker-id 1 :side side 
                                                                            :size size :price price})
                                             agent         (agent 0)]
                                         (dosync
                                           ; Asynchronously send requests (orders), the future by
                                           ; http/post must be realized in order to stop the server
                                           ; without exceptions, so we need extra threads (agents)
                                           ; to send requests
                                           (send-off agent
                                                     (if (= order-type :limit)
                                                       @(http/post (str "http://localhost:" port "/order-new-limit")
                                                                   (assoc options :query-params {:order options-order}) )
                                                       @(http/post (str "http://localhost:" port "/order-new-market")
                                                                   (assoc options :query-params {:order options-order}) )
                                                     )))
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
               (time 
                 (do (start-matching-loop)
                     (let [ask-total (:ask @total)
                           bid-total (:bid @total)]
                        (is (= 0 (min (market-depth @book :ask price)
                                      (market-depth @book :bid price))) )
                        (is (= (max (- bid-total ask-total) (- ask-total bid-total))
                               (max (+ (market-depth @book :ask price) (size-market-orders @book :ask))
                                    (+ (market-depth @book :bid price) (size-market-orders @book :bid)) )))
                        (is (if (= 0 (market-depth @book :ask price)) (< (count @(:market-ask @book)) max-size) true))
                        (is (if (= 0 (market-depth @book :bid price)) (< (count @(:market-bid @book)) max-size) true))
                     )))
               (Thread/sleep idle-time)
               (stop-server)
               ))
    (testing "Bulk test: Send 1000 random-side, randomly priced limit AND market orders and check
              the best bid order is cheaper than the best ask order"
             (clear-book @book)
             (let [stop-server   (denarius.http/start-http book)
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
                                             options-order (json/write-str {:broker-id 1 :side side 
                                                                            :size size :price price})
                                             agent         (agent 0)]
                                         (dosync
                                           ; Asynchronously send requests (orders), the future by
                                           ; http/post must be realized in order to stop the server
                                           ; without exceptions, so we need extra threads (agents)
                                           ; to send requests
                                           (send-off agent
                                                     (if (= order-type :limit)
                                                       @(http/post (str "http://localhost:" port "/order-new-limit")
                                                                   (assoc options :query-params {:order options-order}) )
                                                       @(http/post (str "http://localhost:" port "/order-new-market")
                                                                   (assoc options :query-params {:order options-order}) )
                                                     )))
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
               (time 
                 (do (start-matching-loop)
                     (let [ask-total (:ask @total)
                           bid-total (:bid @total)]
                        (is (= 0 (min (market-depth @book :ask price)
                                      (market-depth @book :bid price))) )
                        (is (if (= 0 (market-depth @book :ask price)) (= (count @(:market-ask @book)) 0) true))
                        (is (if (= 0 (market-depth @book :bid price)) (= (count @(:market-bid @book)) 0) true))
                        (is (< (:price @(first @(best-price-level-ref @book :bid)))
                               (:price @(first @(best-price-level-ref @book :ask)))))
                     )))
               (stop-server)
               ))
    ))
