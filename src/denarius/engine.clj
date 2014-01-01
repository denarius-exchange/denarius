(ns denarius.engine)

; Order book record
(defrecord Book [bid ask])

(defn create-order-book [^String asset-name]
  "Creates an order book instance with the asset name as metadata"
  (with-meta 
    (->Book (ref (sorted-map)) (ref (sorted-map)))
    {:asset-name asset-name} ))

(defn book-asset [^Book book]
  "Retrieves the order book's asset name"
  (:asset-name (meta book))) 

(defn market-depth
  "Retrieves the number of orders at the side indicated in the argument.
   A list of [price-leel count] vectors is returned.
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

; Record for order information
(defrecord Order [order-id broker-id type side size price])

(defn create-order [order-id broker-id type side size price]
  "Create a new order with order-id, customer id (broker), side, size and
   price information"
  (->Order order-id broker-id type side size price ))

(defn create-order-ref [order-id broker-id type side size price key watcher]
  (let [order (create-order order-id broker-id type side size price)]
    (add-watch (ref order) key watcher )))

(defn insert-order
  ([^Book book ^Order order-ref]
    "Insert an order into the book specified as argument"
    (let [side      (:side @order-ref)
          price     (:price @order-ref)
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
  ([^Book book ^Order order-ref key watcher]
    "Insert an order into the book specified as argument with
     watcher function attached to retrieve changes in the reference"
    (add-watch order-ref key watcher) ))


(defn remove-order [^Book book ^Order order-ref]
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


(defn best-price
  ([book side]
    "Compute the best price available for an order of the side
     specified as argument. This searches the opposite site for
     the best price available."
    (let [side-ref (side book)
          minmax   (if (= side :ask) min max) 
          prices   (keys @side-ref)]
      (if (empty? prices)
        nil
        (@side-ref (apply minmax prices)) )))
  ([book side limit]
    "Compute the best price available for an order of the side
     specified as argument. This searches the opposite site for
     the best price available that is better (in the sense of
     cheaper for bid orders and more expensive for ask orders)
     that a given limit."
    (let [side-ref (side book)
          minmax   (if (= side :ask) min max)
          op       (if (= side :ask) >= <=)
          prices   (keys @side-ref)]
      (if (empty? prices)
        nil
        (let [best (apply minmax prices)]
          (if (op limit best)
            (@side-ref best)
            nil ))))))


(defn match-order [^Book book ^Order order-ref cross]
  "Match order. If no order-ref exists in the book, insert the order-ref."
  (let [order-id      (:order-id @order-ref)
        broker-id     (:broker-id @order-ref)
        price         (:price @order-ref)
        order-side    (:side @order-ref)
        matching-side (if (= order-side :bid) :ask :bid)
        side-ref      (matching-side book)]
    (dosync
      (loop [level-ref (best-price book matching-side price)]
        (if level-ref
          (if-not (empty? @level-ref)
            (let [first-available-ref (last @level-ref)
                  first-available     @(last @level-ref)
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
                  (if-not (= size available-size)
                    (recur (best-price book matching-side price) )))))))))))
