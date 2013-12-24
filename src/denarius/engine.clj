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

(defn insert-order [^Book book ^Order order]
  (let [side      (:side order)
        price     (:price order)
        order-ref (ref order)
        side-ref  (side book)
        level-ref (@side-ref price)]
    (if level-ref
      (do
        (dosync (alter level-ref #(conj % order-ref ))))
      (do
        (dosync
          (alter side-ref #(assoc % price (ref ()) ))
          (alter (@side-ref price) #(conj % order-ref)))
        ))
    order-ref ))


(defn remove-order [^Book book ^Order order]
    (let [order-id  (:order-id order)
          side      (:side order)
          price     (:price order)
          side-ref  (side book)
          level-ref (@side-ref price)
          pred      #(= (:order-id @%) order-id)]
      (if level-ref
        (do
          (dosync (alter level-ref #(remove pred %))) true ))))


(defn match-order [^Book book ^Order order]
  (let [order-id      (:order-id order)
        broker-id     (:broker-id order)
        price         (:price order)
        order-side    (:side order)
        matching-side (if (= order-side :bid) :ask :bid)
        side-ref      (matching-side book)
        level-ref     (@side-ref price)]
    (if-not level-ref
      (insert-order book order))
    (dosync
      (loop [size (:size order)]
        (if (empty? @level-ref)
          (insert-order book (create-order order-id broker-id order-side size price))
          (let [first-available-ref (last @level-ref)
                first-available     @(last @level-ref)
                available-size      (:size first-available)]
            (if (> available-size size)
              (let [new-matching (create-order
                                   (:order-id first-available)
                                   (:broker-id first-available)
                                   matching-side
                                   (- available-size size)
                                   price)]
                (dosync (alter first-available-ref (fn [_] new-matching ))))
              (if (= size available-size)
                (remove-order book first-available)
                (do
                  (remove-order book first-available)
                  (recur (- size available-size)) )))))))))
  