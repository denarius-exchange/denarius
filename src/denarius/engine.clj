(ns denarius.engine)

(def book {:bid (ref (sorted-map))  :ask (ref (sorted-map))} )


(defrecord Order-Complete [broker-id order-id side size price])

(defn create-order [broker-id order-id side size price]
  (->Order-Complete broker-id order-id side size price ))

(defn insert-order [^Order-Complete order]
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
