(ns denarius.http-test
  (:use clojure.test
        denarius.engine
        denarius.http)
  (:require [org.httpkit.client :as http]))

(deftest test-http
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
        asset-name "EURUSD"
        options    {:timeout 200             ; ms
                    :basic-auth ["user" "pass"]
                    :user-agent "User-Agent-string"
                    :headers {"X-Header" "Value"}}]
    (testing "Two limit orders sent to the HTTP server. Check if they exist on the book"
             (let [order-book     (ref (create-order-book asset-name))
                   matching-agent (agent 1)
                   options-ask    {:broker-id 1 :side :ask :size 2 :price 10}
                   options-bid    {:broker-id 1 :side :bid :size 3 :price 10}]
               (start-http order-book)
               @(http/post (str "http://localhost:" port "/order-new-limit")
                          (assoc options :query-params options-ask))
               @(http/post (str "http://localhost:" port "/order-new-limit")
                          (assoc options :query-params options-bid))
               (is (= 3 (market-depth @order-book :bid price)))
               (is (= 2 (market-depth @order-book :ask price))) ))
    ))
