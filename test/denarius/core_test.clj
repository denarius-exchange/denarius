(ns denarius.core-test
  (:use clojure.core
        clojure.test
        clojure.tools.logging
        denarius.core
        denarius.engine)
  (:require [org.httpkit.client :as http]
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
        cross      (fn [order-in-book order-incoming size price]
                     (dosync (ref-set last-price [price size])))
        asset-name "EURUSD"
        options    {:timeout 200             ; ms
                    :basic-auth ["user" "pass"]
                    :user-agent "User-Agent-string"
                    :headers {"X-Header" "Value"}}
        port        denarius.http/port
        idle-time   2000]
    (testing "Two limit orders sent to the HTTP server. Check that they are matched after some time"
             (clear-book @book)
             (let [options-ask    {:broker-id 1 :side :ask :size 1 :price 10}
                   options-bid    {:broker-id 1 :side :bid :size 1 :price 10}
                   stop-server    (denarius.http/start-http book)]
               (start-matching-loop)
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-ask))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-bid))
               (Thread/sleep idle-time)
               (is (= 0 (market-depth @book :bid price)))
               (is (= 0 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Four limit orders sent to the HTTP server. Check that they are matched after some time"
             (clear-book @book)
             (let [options-ask-1  {:broker-id 1 :side :ask :size 1 :price 10}
                   options-ask-2  {:broker-id 1 :side :ask :size 2 :price 10}
                   options-bid-1  {:broker-id 1 :side :bid :size 2 :price 10}
                   options-bid-2  {:broker-id 1 :side :bid :size 2 :price 10}
                   stop-server    (denarius.http/start-http book)]
               (start-matching-loop)
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-ask-1))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-bid-1))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-bid-2))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-ask-2))
               (Thread/sleep idle-time)
               (is (= 1 (market-depth @book :bid price)))
               (is (= 0 (market-depth @book :ask price)))
               (stop-server) ))
    (testing "Two limit orders sent to the HTTP server (2 to 1). Check that they are partially 
              matched after some time"
             (clear-book @book)
             (let [options-ask    {:broker-id 1 :side :ask :size 2 :price 10}
                   options-bid    {:broker-id 1 :side :bid :size 1 :price 10}
                   stop-server    (denarius.http/start-http book)]
               (start-matching-loop)
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-ask))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                           (assoc options :query-params options-bid))
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
                                             options-order {:broker-id 1 :side side :size size :price 10}
                                             agent         (agent 0)]
                                         (dosync
                                           ; Asynchronously send requests (orders), the future by
                                           ; http/post must be realized in order to stop the server
                                           ; without exceptions, so we need extra threads (agents)
                                           ; to send requests
                                           (send-off agent
                                                     @(http/post (str "http://localhost:" port "/order-new-limit")
                                                                 (assoc options :query-params options-order) )
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
               @(time (start-matching-loop))
               (Thread/sleep idle-time)
               (let [ask-total (:ask @total)
                     bid-total (:bid @total)]
                 (is (= (max 0 (- bid-total ask-total)) (market-depth @book :bid price)))
                 (is (= (max 0 (- ask-total bid-total)) (market-depth @book :ask price))) )
               (stop-server) ))
    ))
