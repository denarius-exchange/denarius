(ns denarius.engine-test)

(use 'clojure.test
     'denarius.engine)


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
             (is (= 0 (market-depth order-book :bid price))) )))


(deftest test-market-depth
  (let [price-10   10
        price-20   20
        type       :limit
        size       1
        broker-id  1
        order-10-1 (create-order-ref 1 broker-id type :bid size price-10 nil nil)
        order-10-2 (create-order-ref 2 broker-id type :bid size price-10 nil nil)
        order-20-1 (create-order-ref 3 broker-id type :bid size price-20 nil nil)
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
             (is (= '([10 2] [20 1]) (market-depth order-book :bid)))) ))


(deftest test-match-order
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
          last       (ref nil)
          cross      (fn [order-in-book order-incoming size price]
                       (dosync (ref-set last [price size])))
          asset-name "EURUSD"]
      (testing "Matching order (existing is bid, incoming is ask): Equal size"
               (let [order-book (create-order-book asset-name)
                     order-bid-1 (create-order-ref order-id-1 broker-id type :bid size price key watcher)
                     order-ask-1 (create-order-ref order-id-3 broker-id type :ask size price key watcher)]
                 (insert-order order-book order-bid-1)
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
                 (is (= (list order-id-2 1) @result-bid))
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
                 (is (= (list order-id-2 1) @result-bid))
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
