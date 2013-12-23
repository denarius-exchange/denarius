(ns denarius.engine)

(def order-book {:bid (ref (sorted-map))  :ask (ref (sorted-map))} )


(defrecord Order-Complete [order-id broker-id side size price])

(defn create-order [order-id broker-id side size price]
  (->Order-Complete order-id broker-id side size price ))

(defn insert-order [book ^Order-Complete order]
  (let [side (:side order)
        price (:price order)
        side-ref (side book)
        level-ref (@side-ref price)]
    (if level-ref
      (do
        (dosync
          (alter level-ref #(conj % order))))
      (do
        (dosync
          (alter side-ref #(assoc % price (ref ()) ))
          (alter (@side-ref price) #(conj % order)))
        ))))
