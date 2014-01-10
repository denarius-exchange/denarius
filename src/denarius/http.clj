(ns denarius.http
  (:use [clojure.tools.logging :only [info debug]]
        org.httpkit.server
        org.httpkit.timer
        (compojure [core :only [defroutes GET POST]]
                   [handler :only [site]]
                   [route :only [files not-found]])
        denarius.order
        [denarius.engine :only [insert-order
                                match-order]])
  (:require [org.httpkit.client :as http]
            [compojure.route :as route]
            [clojure.data.json :as json]))


(def port 8081)

(def book (ref nil))

(defn- wrap-request-logging [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [resp (handler req)]
      (debug (name request-method) (:status resp)
            (if-let [qs (:query-string req)]
              (str uri "?" qs) uri))
      resp)))

(def registered-channel (ref nil))

(defn on-register-listener [req]
  (with-channel req channel
    (dosync (ref-set registered-channel channel)) ))

(defn increment-position []
  (fn [order-ref-1 order-ref-2 size price]
    (if-not (nil? @registered-channel)
      (send! @registered-channel
             (json/json-str {:status 200
                             :headers {"Content-Type" "text/plain"}
                             :body (str "Order" (:order-id @order-ref-1)
                                        " partilly executed size: " size)})
             false) )
    ))

(defn parse-params [req channel order-type]
  (let [params       (:order (:params req))
        order-params (for [l [:broker-id
                              :side
                              :size
                              :price]] (l (json/read-str params
                                                         :key-fn keyword) ))
        [broker-id 
         side-str 
         size price] order-params
        side         (if (= side-str "bid") :bid :ask)
        order-ref    (create-order-ref (get-order-id) broker-id order-type side
                                       (Integer. size) (Integer. price) nil
                                       [(increment-position)])]
    [broker-id side size price order-ref]))
    

(defn on-order-new-limit [req]
  (with-channel req channel
    (let [[broker-id side size price order-ref] (parse-params req channel :limit)]
      (insert-order @book order-ref)
      (send! channel
             {:status 200
              :headers {"Content-Type" "text/plain"}
              :body (str "New limit!")} true ) )))

(defn on-order-new-market [req]
  (with-channel req channel
    (let [[broker-id side size price order-ref] (parse-params req channel :market)]
      (insert-order @book order-ref)
      (send! channel 
             {:status 200
              :headers {"Content-Type" "text/plain"}
              :body (str "New market!")} true ))))
  
;(json-str (get-msgs (@clients channel)))   
(defroutes routes
  (POST "/order-new-limit" [] on-order-new-limit)
  (POST "/order-new-market" [] on-order-new-market)
  (POST "/register-listener" [] on-register-listener)
  (route/not-found "<h1>Page not found</h1>"))

(defn start-http [order-book]
  (dosync (ref-set book @order-book))
  
  (info "Starting server on port" port)
  (run-server (-> #'routes site wrap-request-logging) {:port port})
  )