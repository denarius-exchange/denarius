(ns denarius.engine)

; This is the order book record
(defrecord Book [bid ask])

(defn create-order-book [asset-name]
   (with-meta 
     (->Book (ref (sorted-map)) (ref (sorted-map)))
     {:asset-name asset-name} ))

(defn market-depth
  ([order-book side]
    (map (fn [level]
           [(key level) (count @(val level))])
         @(side order-book)))
  ([order-book side price]
    (let [side-ref (side order-book)
          level-ref (@side-ref price)]
    (if level-ref
      (count @(@(side order-book) price))
      0 ))))

(defrecord Order [order-id broker-id side size price])

(defn create-order [order-id broker-id side size price]
  (->Order order-id broker-id side size price ))

(defn insert-order [book ^Order order]
  (let [side (:side order)
        price (:price order)
        side-ref (side book)
        level-ref (@side-ref price)]
    (if level-ref
      (do
        (dosync (alter level-ref #(conj % order))))
      (do
        (dosync
          (alter side-ref #(assoc % price (ref ()) ))
          (alter (@side-ref price) #(conj % order)))
        ))))


(defn remove-order [book ^Order order]
    (let [order-id (:order-id order)
          side (:side order)
          price (:price order)
          side-ref (side book)
          level-ref (@side-ref price)
          pred #(= (:order-id % order-id))]
      (if level-ref
        (do
          (dosync (alter level-ref #(remove pred %))) ))))


(defn match-order [book ^Order order]
  (let [price         (:price order)
        order-side    (:side order)
        matching-side (if (= order-side :bid) :ask :bid)
        side-ref      (matching-side book)
        level-ref     (@side-ref price)]
    (if-not level-ref
      (insert-order order))
    (loop []
      (if-not (empty? level-ref)
        (insert-order order) ))))
  
  
