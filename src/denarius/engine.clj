(ns denarius.engine
  (:use denarius.order)
  (:import [denarius.order Order]) )

; Order book record
(defrecord Book [bid ask
                 market-bid market-ask])

(defn create-order-book [^String asset-name]
  "Creates an order book instance with the asset name as metadata"
  (with-meta 
    (->Book (ref (sorted-map)) (ref (sorted-map))
            (ref (list)) (ref (list)))
    {:asset-name asset-name} ))

(defn book-asset [^Book book]
  "Retrieves the order book's asset name"
  (:asset-name (meta book))) 

(defn count-orders
  "Retrieves the number of orders at the side indicated in the argument.
   A list of [price-level count] vectors is returned.
   An additional parameter price indicates the specific price level to
   constrain the count."
  ([^Book order-book side]
    (map (fn [level]
           [(key level) (count @(val level))])
         @(side order-book)))
  ([order-book side price]
    (let [side-ref (side order-book)
          level-ref (@side-ref price)]
      (if level-ref
        (count @(@(side order-book) price))
        0 ))))

(defn order-market-side [book side]
  (if (= side :ask)
    (:market-ask book)
    (:market-bid book) ))

(defn market-depth
  ([^Book order-book side]
    (map (fn [level]
           [(key level) (apply + (map (comp :size deref) @(val level)) )])
         @(side order-book)) )
  ([^Book order-book side price]
    (let [side-ref (side order-book)
          level-ref (@side-ref price)]
      (if level-ref
        (apply + (map (comp :size deref) @(@(side order-book) price)))
        0 )) ))

(defn size-market-orders [^Book order-book side]
  (apply + (map (comp :size deref) @(order-market-side order-book side)) ))


(def order-type-dispatch (fn [_ ^Order order-ref & more] (:type @order-ref)))

(defmulti insert-order order-type-dispatch)

(defmethod insert-order :limit [^Book book ^Order order-ref]
  "Insert an order into the book specified as argument"
  (let [side      (:side @order-ref)
        price     (:price @order-ref)
        side-ref  (side book)
        level-ref (@side-ref price)]
    (dosync 
      (if level-ref
        (alter level-ref #(conj % order-ref ))
        (do
          (alter side-ref #(assoc % price (ref ()) ))
          (alter (@side-ref price) #(conj % order-ref)) )))))

(defmethod insert-order :market [^Book book ^Order order-ref]
  (let [side     (:side @order-ref)
        side-ref (order-market-side book side)]
    (dosync
      (alter side-ref #(conj % order-ref)) )))


(defmulti remove-order order-type-dispatch)

(defmethod remove-order :limit [^Book book ^Order order-ref]
  "Remove an order from the book specified as argument"
  (let [order-id  (:order-id @order-ref)
        side      (:side @order-ref)
        price     (:price @order-ref)
        side-ref  (side book)
        level-ref (@side-ref price)
        pred      #(= (:order-id @%) order-id)]
    (if level-ref
      (do
        (dosync (alter level-ref #(remove pred %))) true ))))

(defmethod remove-order :market [^Book book ^Order order-ref]
  "Remove an order from the book specified as argument"
  (let [order-id  (:order-id @order-ref)
        side      (:side @order-ref)
        side-ref  (order-market-side book side)
        pred      #(= (:order-id @%) order-id)]
    (dosync (alter side-ref #(remove pred %))) true ))


(defn best-price-level-ref
  ([book side]
    "Compute the best price available for an order of the side
     specified as argument. This searches the opposite site for
     the best price available."
    (let [side-ref         (side book)
          minmax           (if (= side :ask) min max) 
          cmp              (if (= side :ask) < >) 
          prices           (sort cmp (keys @side-ref))
          num-lvls-minus-1 (dec (count prices))]
      (if (empty? prices)
        nil
        (loop [index 0]
          (let [current-level (nth prices index)
                level-ref (@side-ref current-level)]
            (if-not (empty? @level-ref)
              level-ref
              (if (= index num-lvls-minus-1)
                nil
                (recur (inc index)) )))))))
  ([book side limit]
    "Compute the best price available for an order of the side
     specified as argument. This searches the opposite site for
     the best price available that is better in the sense of
     cheaper for bid orders and more expensive for ask orders
     that a given limit."
    (let [side-ref         (side book)
          minmax           (if (= side :ask) min max)
          op               (if (= side :ask) >= <=)
          cmp              (if (= side :ask) < >) 
          prices           (sort cmp (keys @side-ref))
          num-lvls-minus-1 (dec (count prices))]
      (if (empty? prices)
        nil
        (loop [index 0]
          (let [current-level (nth prices index)
                level-ref (@side-ref current-level)]
            (if-not (op limit current-level)
              nil
              (if-not (empty? @level-ref)
                level-ref
                (if (= index num-lvls-minus-1)
                  nil
                  (recur (inc index)) )))))))))


(defn clear-book [book]
  "Clears book"
  (dosync
    (let [ask     (:ask book)
          bid     (:bid book)
          mkt-ask (:market-ask book)
          mkt-bid (:market-bid book)]
      (doseq [price (keys @ask)]
        (alter ask dissoc price) )
      (doseq [price (keys @bid)]
        (alter bid dissoc price) )
      (ref-set mkt-ask (list))
      (ref-set mkt-bid (list)) )))


(defmulti match-order order-type-dispatch)

(defmethod match-order :limit [^Book book ^Order order-ref cross]
  "Match order. If no order-ref exists in the book, insert the order-ref."
  (let [order-id      (:order-id @order-ref)
        broker-id     (:broker-id @order-ref)
        price         (:price @order-ref)
        order-side    (:side @order-ref)
        matching-side (if (= order-side :bid) :ask :bid)
        side-ref      (matching-side book)]
    (loop []
      (dosync
        (let [level-ref (best-price-level-ref book matching-side price)]
          (if level-ref
            (if-not (empty? @level-ref)
              (let [first-available-ref (last @level-ref)
                    first-available     @first-available-ref
                    available-size      (:size first-available)
                    size                (:size @order-ref)]
                (cross first-available-ref order-ref (min available-size size) price)
                (if (> available-size size)
                  (do
                    (alter first-available-ref update-in [:size] - size)
                    (remove-order book order-ref) )
                  (do
                    (alter order-ref update-in [:size] - available-size)
                    (alter first-available-ref update-in [:size] - available-size)
                    (remove-order book first-available-ref)
                    (if (= size available-size)
                      (remove-order book order-ref)
                      (recur  ))))))))))))

(defmethod match-order :market [^Book book ^Order order-ref cross]
  (let [order-id      (:order-id @order-ref)
        broker-id     (:broker-id @order-ref)
        order-side    (:side @order-ref)
        matching-side (if (= order-side :bid) :ask :bid)
        mkt-side      (order-market-side book order-side)
        mkt-mtch-side (if (= matching-side :bid) :market-bid :market-ask)
        mkt-mtch-ref  (mkt-mtch-side book)]
    (loop []
      (dosync
        (let [best-lvl-ref (best-price-level-ref book matching-side)
              level-ref  (if-not (empty? @mkt-mtch-ref)
                           mkt-mtch-ref
                           best-lvl-ref)]
          (if (and level-ref best-lvl-ref (-> @level-ref empty? not))
            (let [first-available-ref (last @level-ref)
                  first-available     @first-available-ref
                  available-size      (:size first-available)
                  size                (:size @order-ref)
                  price               (:price @(first @best-lvl-ref))]
              (cross first-available-ref order-ref (min available-size size) 
                     price)
              (if (> available-size size)
                (do
                  (alter first-available-ref update-in [:size] - size)
                  (remove-order book order-ref))
                (do
                  (alter order-ref update-in [:size] - available-size)
                  (alter first-available-ref update-in [:size] - available-size)
                  (remove-order book first-available-ref)
                  (if (= size available-size)
                    (remove-order book order-ref)
                    (recur )))))))))))

(defn match-once [book cross]
  (let [market-ask (:market-ask book)
        market-bid (:market-bid book)
        ask        (:ask book)
        bid        (:bid book)]
    (dosync
      (let [order-ref (if-not (empty? @market-ask)
                        (last @market-ask)
                        (if-not (empty? @market-bid)
                          (last @market-bid)
                          (if (> (apply + (map second (market-depth book :ask))) 0)
                            (last @(best-price-level-ref book :ask))
                            nil)))]
        (if order-ref
          (match-order book order-ref cross) )))))


(defn start-matching-loop [book cross-function]
  (future
    (while true
      (do ;(java.lang.Thread/sleep 1)
        (try
          (match-once @book cross-function )
          (catch Exception e)) ))))