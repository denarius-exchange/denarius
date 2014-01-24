(ns denarius.engine-test
  (:use clojure.core
        clojure.test
        denarius.order
        denarius.engine))


(deftest test-create-book
  (testing "Create book (EURUSD)"
           (let [asset-name "EURUSD"
                 order-book (create-order-book asset-name)]
             (is (= "EURUSD" (:asset-name (meta order-book)))) )))


(deftest test-create-order-buy
  (testing "Create order"
           (let [order-id   1111
                 broker-id  1
                 type       :limit
                 price      10
                 size       1
                 side       :bid
                 order      (create-order order-id broker-id type side size price)]
             (is (= order-id (:order-id order)))
             (is (= broker-id (:broker-id order)))
             (is (= side (:side order)))
             (is (= size (:size order)))
             (is (= price (:price order))) )))


(deftest test-insert-into-book-and-delete
  (let [order-id   1111
        broker-id  1
        type       :limit
        price      10
        size       1
        side       :bid
        order      (create-order-ref order-id broker-id type side size price nil nil)
        asset-name "EURUSD"
        order-book (create-order-book asset-name)]
    (testing "Insert order into book"
             (insert-order order-book order)
             (is (= 1 (market-depth order-book :bid price))) )
    (testing "Delete order from book"
             (remove-order order-book order)
             (is (= 0 (market-depth order-book :bid price))))))


(deftest test-insert-market-and-delete
  (let [order-id   1111
        broker-id  1
        type       :market
        size       1
        side       :bid
        order-1 (create-order-ref 1 broker-id type :bid size nil nil nil)
        order-2 (create-order-ref 2 broker-id type :bid size nil nil nil)
        asset-name "EURUSD"
        order-book (create-order-book asset-name)]
    (testing "Insert market orders into book"
             (insert-order order-book order-1)
             (insert-order order-book order-2)
             (is (= 2 (count @(:market-bid order-book)))))
    (testing "Delete first market order from book"
             (remove-order order-book order-1)
             (is (= 1 (count @(:market-bid order-book)))) )
    (testing "Delete all market orders from book (including a non-existing)"
             (remove-order order-book order-1)
             (remove-order order-book order-2)
             (is (= 0 (count @(:market-bid order-book)))) )))


(deftest test-market-depth
  (let [price-10   10
        price-20   20
        type       :limit
        size       1
        broker-id  1
        order-10-1 (create-order-ref 1 broker-id type :bid size price-10 nil nil)
        order-10-2 (create-order-ref 2 broker-id type :bid size price-10 nil nil)
        order-20-1 (create-order-ref 3 broker-id type :bid size price-20 nil nil)
        order-20-2 (create-order-ref 4 broker-id type :bid (* 3 size) price-20 nil nil)
        asset-name "EURUSD"
        order-book (create-order-book asset-name)]
    (insert-order order-book order-10-1)
    (insert-order order-book order-10-2)
    (insert-order order-book order-20-1)
    (testing (str "Market Depth price" price-10)
             (is (= 2 (market-depth order-book :bid price-10))))
    (testing (str "Market Depth price" price-20)
             (is (= 1 (market-depth order-book :bid price-20))))
    (testing (str "Market Depth price all bids")
             (is (= '([10 2] [20 1]) (market-depth order-book :bid))))
    (insert-order order-book order-20-2)
    (testing (str "Market Depth price" price-20)
             (is (= 4 (market-depth order-book :bid price-20))))))


(deftest test-match-order-limit
  (let [order-id-1 1
        order-id-2 2
        order-id-3 3
        order-id-4 4
        broker-id  1
        type       :limit
        price      10
        size       1
        result-bid (ref nil)
        result-ask (ref nil)
        key        :fulfill
        watcher    (fn [k r o n]
                     (let [result-ref (if (= (:side n) :bid) result-bid result-ask)]
                       (dosync (ref-set result-ref
                                        (list (:order-id n) (:size n)) ))))
        last-price (ref nil)
        cross      (fn [order-in-book order-incoming size price]
                     (dosync (ref-set last-price [price size])))
        asset-name "EURUSD"]
    (testing "Matching order (existing is bid, incoming is ask): Equal size"
             (let [order-book (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size price key watcher)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price key watcher)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-ask-1)
               (match-order order-book order-ask-1 cross)
               (is (= 0 (market-depth order-book :bid price )))
               (is (= [price size])) ))
    (testing "Matching order (existing is ask, incoming is bid): Equal size"
             (let [order-book (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size price key watcher)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price key watcher)]
               (insert-order order-book order-ask-1)
               (insert-order order-book order-bid-1)
               (match-order order-book order-bid-1 cross)
               (is (= 0 (market-depth order-book :ask price )))
               (is (= [price size])) ))
    (testing "Matching order (existing is ask, incoming is bid): Partial fulfilling, 1 to 2"
             (let [order-book (create-order-book asset-name)
                   order-bid-2 (create-order-ref order-id-2 broker-id type :bid (* 2 size) price key watcher)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price key watcher)]
               (insert-order order-book order-ask-1)
               (insert-order order-book order-bid-2)
               (match-order order-book order-bid-2 cross)
               (is (= 0 (market-depth order-book :ask price )))
               (is (= 1 (market-depth order-book :bid price )))
               (is (= [price size])) ))
    (testing "Matching order (existing is bid, incoming is ask): Partial fulfilling, 2 to 1"
             (let [order-book (create-order-book asset-name)
                   order-bid-2 (create-order-ref order-id-2 broker-id type :bid (* 2 size) price key watcher)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price key watcher)]
               (insert-order order-book order-bid-2)
               (insert-order order-book order-ask-1)
               (match-order order-book order-ask-1 cross)
               (is (= 0 (market-depth order-book :ask price )))
               (is (= 1 (market-depth order-book :bid price )))
               (is (= [price size])) ))
    (testing "Matching order (existing bid, incoming cheaper ask; Partial fulfilling, 2 to 1"
             (let [order-book (create-order-book asset-name)
                   order-bid-2 (create-order-ref order-id-2 broker-id type :bid (* 2 size) price key watcher)
                   order-ask-2 (create-order-ref order-id-4 broker-id type :ask size (- price 1) key watcher)]
               (insert-order order-book order-bid-2)
               (insert-order order-book order-ask-2)
               (match-order order-book order-ask-2 cross)
               (is (= 1 (market-depth order-book :bid price )))
               (is (= [(- price 1) size])) ))
    (testing "Matching order (existing cheaper ask, incoming bid; Partial fulfilling, 1 to 2"
             (let [order-book (create-order-book asset-name)
                   order-bid-2 (create-order-ref order-id-2 broker-id type :bid (* 2 size) price key watcher)
                   order-ask-2 (create-order-ref order-id-4 broker-id type :ask size (- price 1) key watcher)]
               (insert-order order-book order-ask-2)
               (insert-order order-book order-bid-2)
               (match-order order-book order-bid-2 cross)
               (is (= 1 (market-depth order-book :bid price )))
               (is (= [(- price 1) size])) ))))

(deftest test-match-order-market
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
        asset-name "EURUSD"]
    (testing "If no book orders exist, stack market orders until we have a price"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size nil nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size nil nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-ask-1)
               (match-order order-book order-ask-1 cross)
               (is (= 1 (count @(:market-bid order-book ))))
               (is (= 1 (count @(:market-ask order-book )))) ))
    (testing "If limit and market orders exist, match with market orders first, but at bidding price"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size nil nil nil)
                   order-bid-2 (create-order-ref order-id-2 broker-id type-lmt :bid size 10 nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size nil nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-bid-2)
               (insert-order order-book order-ask-1)
               (match-order order-book order-ask-1 cross)
               (is (= 0 (count @(:market-bid order-book ))))
               (is (= 0 (count @(:market-ask order-book ))))
               (is (= 1 (market-depth order-book :bid price))) ))
    (testing "Match with market orders and limit orders (incoming size 2 for 1 + 1)"
             (let [order-book  (create-order-book asset-name)
                   order-ask-1 (create-order-ref order-id-1 broker-id type :ask size nil nil nil)
                   order-ask-2 (create-order-ref order-id-2 broker-id type-lmt :ask size 10 nil nil)
                   order-bid-1 (create-order-ref order-id-3 broker-id type :bid (* 2 size) nil nil nil)]
               (insert-order order-book order-ask-1)
               (insert-order order-book order-ask-2)
               (insert-order order-book order-bid-1)
               (match-order order-book order-bid-1 cross)
               (is (= 0 (count @(:market-bid order-book ))))
               (is (= 0 (count @(:market-ask order-book ))))
               (is (= 0 (market-depth order-book :ask price))) ))
    (testing "Match with market orders and limit orders (incoming size 2 for 2 + 1)"
             (let [order-book  (create-order-book asset-name)
                   order-ask-1 (create-order-ref order-id-1 broker-id type :ask (* 2 size) nil nil nil)
                   order-ask-2 (create-order-ref order-id-2 broker-id type-lmt :ask size 10 nil nil)
                   order-bid-1 (create-order-ref order-id-3 broker-id type :bid (* 2 size) nil nil nil)]
               (insert-order order-book order-ask-1)
               (insert-order order-book order-ask-2)
               (insert-order order-book order-bid-1)
               (match-order order-book order-bid-1 cross)
               (is (= 0 (count @(:market-bid order-book ))))
               (is (= 0 (count @(:market-ask order-book ))))
               (is (= 1 (market-depth order-book :ask price))) ))
    (testing "Match with market orders and limit orders (incoming size 2 for 2 + 1)"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size nil nil nil)
                   order-bid-2 (create-order-ref order-id-2 broker-id type-lmt :bid (* 2 size) 10 nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask (* 2 size) nil nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-bid-2)
               (insert-order order-book order-ask-1)
               (match-order order-book order-ask-1 cross)
               (is (= 0 (count @(:market-ask order-book ))))
               (is (= 0 (count @(:market-bid order-book ))))
               (is (= 1 (market-depth order-book :bid price))) ))
    ))



(deftest test-match-once
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
        asset-name "EURUSD"]
    (testing "If no book orders exist, stack market orders until we have a price"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size price nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-ask-1)
               (match-once order-book (fn [a b c d] nil))
               (is (= 1 (count @(:market-bid order-book ))))
               (is (= 1 (count @(:market-ask order-book )))) ))
    (testing "Match two limit orders"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type-lmt :bid size price nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type-lmt :ask size price nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-ask-1)
               (match-once order-book (fn [a b c d] nil))
               (is (= 0 (market-depth order-book :bid price)))
               (is (= 0 (market-depth order-book :ask price))) ))
    (testing "Receive a market order, stack it and then match it with an incoming limit"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type-lmt :bid size price nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-ask-1)
               (match-once order-book (fn [a b c d] nil))
               (is (= 0 (count @(:market-bid order-book ))))
               (is (= 0 (market-depth order-book :ask price))) ))
    (testing "Receive two market orders, stack them until getting a limit order"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size price nil nil)
                   order-bid-2 (create-order-ref order-id-2 broker-id type-lmt :bid size price nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-ask-1)
               (insert-order order-book order-bid-2)
               (match-once order-book (fn [a b c d] nil))
               (is (= 0 (count @(:market-bid order-book ))))
               (is (= 0 (count @(:market-ask order-book ))))
               (is (= 1 (market-depth order-book :bid price))) ))
    (testing "Receive two market orders (one triple in size), stack them until getting a limit order. A market of size one survives"
             (let [order-book  (create-order-book asset-name)
                   order-bid-1 (create-order-ref order-id-1 broker-id type :bid size price nil nil)
                   order-bid-2 (create-order-ref order-id-2 broker-id type-lmt :bid (* 2 size) price nil nil)
                   order-ask-1 (create-order-ref order-id-3 broker-id type :ask (* 4 size) price nil nil)]
               (insert-order order-book order-bid-1)
               (insert-order order-book order-ask-1)
               (insert-order order-book order-bid-2)
               (match-once order-book (fn [a b c d] nil))
               (is (= 0 (count @(:market-bid order-book ))))
               (is (= 1 (count @(:market-ask order-book ))))
               (is (= 0 (market-depth order-book :bid price))) ))
    ))


(deftest test-bulk
  (let [broker-id  1
        total-size (ref 0)
        cross      (fn [order-1 order-2 size price]
                     (dosync (alter total-size + size)))
        callback   (fn [] nil)
        asset-name "EURUSD"]
    (testing "Bulk test performance"
             (let [order-book  (create-order-book asset-name)
                   total-num   1000
                   max-time    10000
                   order-loop  (fn []
                                 (loop [order-id  1
                                        total-bid 0
                                        total-ask 0]
                                   (let [side       (if (= 0 (rand-int 2)) :bid :ask)
                                         size       (inc (rand-int 10))
                                         price      10
                                         order      (create-order-ref order-id broker-id :limit 
                                                                      side size price nil callback)]
                                     (insert-order order-book order) 
                                     (if (> order-id total-num)
                                       [total-bid total-ask]
                                       (recur (inc order-id)
                                              (if (= :bid side)
                                                (+ total-bid size)
                                                total-bid)
                                              (if (= :ask side)
                                                (+ total-ask size)
                                                total-ask ))))))
                   time-test   (fn []
                                 (let [time-increment 25]
                                   (loop [time-counter 0]
                                     (if (> time-counter max-time)
                                       time-counter   ; exit failing
                                       (if (<= total-num @total-size)
                                         time-counter ; exit passing
                                         (do
                                           (Thread/sleep time-increment)
                                           (recur (+ time-counter time-increment)) ))))))]
               (order-loop)
               (start-matching-loop (ref order-book) cross)
               (Thread/sleep 50)
               (let [time-taken (time-test)]
                 (println "Time taken:" time-taken)
                 (is (< time-taken max-time)) )))))

