(ns denarius.engine
  (:use denarius.order
        carica.core)
  (:require [clojure.core.async :as async])
  (:import [denarius.order Order]) )

; Order book record
(defrecord Book [bid ask
                 market-bid market-ask])

(defn create-order-book [^String asset-name]
  "Creates an order book instance with the asset name as metadata"
  (with-meta 
    (->Book (ref (sorted-map)) (ref (sorted-map))
            (ref []) (ref []))
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
  "Returns the market side book keys from a market order"
  (if (= side :ask)
    (:market-ask book)
    (:market-bid book) ))

(defn market-depth
  ([^Book order-book side]
    "Computes the market depth (all outstanding limit orders) for a
     side of the book. This returns a list of vectors whose first entry
     is the price and second is the aggregated size of all orders with
     that price."
    (map (fn [level]
           [(key level) (apply + (map (comp :size deref) @(val level)) )])
         @(side order-book)) )
  ([^Book order-book side price]
    "Computes the market depth (all outstanding limit orders) that remain
     outstanding at the requested size with the requested price. It returns
     the number of orders."
    (let [side-ref (side order-book)
          level-ref (@side-ref price)]
      (if level-ref
        (apply + (map (comp :size deref) @(@(side order-book) price)))
        0 )) ))

(defn size-market-orders [^Book order-book side]
  "Returns the aggregated size of the outstanding market orders at one side.
   Notice that this is stable just in case there are no orders at the
   opposite side."
  (apply + (map (comp :size deref) @(order-market-side order-book side)) ))


; This is the dispatch function for insert-order, to distinguish by order type
(def order-type-dispatch (fn [_ ^Order order-ref & more] (:type @order-ref)))

(defmulti insert-order order-type-dispatch)

(defmethod insert-order :limit [^Book book ^Order order-ref]
  "Insert a limit order into the book specified as argument"
  (let [side      (:side @order-ref)
        price     (:price @order-ref)
        side-ref  (side book)
        level-ref (@side-ref price)]
    (dosync 
      (alter order-ref
             #(vary-meta % assoc :epoch (java.lang.System/currentTimeMillis) )) 
      (if level-ref
        (alter level-ref #(conj % order-ref ))
        (do
          (alter side-ref #(assoc % price (ref []) ))
          (alter (@side-ref price) #(conj % order-ref)) )))))

(defmethod insert-order :market [^Book book ^Order order-ref]
  "Inserts a market order into the book"
  (let [side     (:side @order-ref)
        side-ref (order-market-side book side)
        cl       (class @side-ref)]
    (dosync
      (alter order-ref
             #(vary-meta % assoc :epoch (java.lang.System/currentTimeMillis) ))
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
      (ref-set mkt-ask [])
      (ref-set mkt-bid []) )))


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
              (let [first-available-ref (first @level-ref)
                    first-available     @first-available-ref
                    available-size      (:size first-available)
                    size                (:size @order-ref)]
                (cross order-ref first-available-ref (min available-size size) price)
                (if (> available-size size)
                  (do
                    (alter first-available-ref update-in [:size] - size)
                    (alter (@(order-side book) price) #(subvec % 1)))
                  (do
                    (alter order-ref update-in [:size] - available-size)
                    (alter first-available-ref update-in [:size] - available-size)
                    (alter level-ref #(subvec % 1))
                    (if (= size available-size)
                      (alter (@(order-side book) price) #(subvec % 1))
                      (recur  ))))))))))))

(defmethod match-order :market [^Book book ^Order order-ref cross]
  "Matches a market order, first trying to get a market oder at the other side
   to match them both or, in case non exists, match again the best limit order
   available."
  (let [order-id      (:order-id @order-ref)
        broker-id     (:broker-id @order-ref)
        order-side    (:side @order-ref)
        matching-side (if (= order-side :bid) :ask :bid)
        mkt-side      (order-market-side book order-side)
        mkt-mtch-side (if (= matching-side :bid) :market-bid :market-ask)
        mkt-mtch-ref  (mkt-mtch-side book)]
    (loop []
      (dosync
        (let [best-lvl-mtc (best-price-level-ref book matching-side)
              best-lvl-ref (if best-lvl-mtc best-lvl-mtc
                             (best-price-level-ref book order-side))
              level-ref    (if-not (empty? @mkt-mtch-ref)
                             mkt-mtch-ref
                             best-lvl-ref)]
          (if (and level-ref best-lvl-ref (-> @level-ref empty? not))
            (let [first-available-ref (first @level-ref)
                  first-available     @first-available-ref
                  available-size      (:size first-available)
                  size                (:size @order-ref)
                  price               (:price @(first @best-lvl-ref))]
              (cross order-ref first-available-ref (min available-size size) 
                     price)
              (if (> available-size size)
                (do
                  (alter first-available-ref update-in [:size] - size)
                  (alter mkt-side #(subvec % 1)))
                (do
                  (alter order-ref update-in [:size] - available-size)
                  (alter first-available-ref update-in [:size] - available-size)
                  (alter level-ref #(subvec % 1))
                  (if (= size available-size)
                    (alter mkt-side #(subvec % 1))
                    (recur) ))))))))))

(defn match-once [book cross]
  "Selects the next order to be matched. Policy is: Choose the oldest market
   order of either bid or ask side, and then select the oldest limit order."
  (let [market-ask (:market-ask book)
        market-bid (:market-bid book)
        ask        (:ask book)
        bid        (:bid book)]
    (dosync
      (let [market-ask (first @market-ask)
            market-bid (first @market-bid)
            limit-ask  (if (> (apply + (map second (market-depth book :ask))) 0)
                            (first @(best-price-level-ref book :ask)))
            limit-bid  (if (> (apply + (map second (market-depth book :bid))) 0)
                            (first @(best-price-level-ref book :bid)))
            long-max   (java.lang.Long/MAX_VALUE)
            slct-epch  (fn [x] (if x (-> @x meta :epoch) long-max))
            order-mkt  (min-key slct-epch market-ask market-bid)
            order-lmt  (min-key slct-epch limit-ask limit-bid)
            order-ref  (if order-mkt order-mkt order-lmt)]
        (if order-ref
          (match-order book order-ref cross) )))))


(defn start-matching-loop [book cross-function async-ch]
  "Starts an infinite loop that will call match-once"
  (future 
    (while true ;(java.lang.Thread/sleep 1)
      (async/<!! async-ch)  ; block here until inserting order
      (try
        (match-once @book cross-function )
        (catch Exception e (println e) false)) )))