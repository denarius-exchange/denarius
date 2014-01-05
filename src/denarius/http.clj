(ns denarius.http
  (:use [clojure.tools.logging :only [info debug]]
        org.httpkit.server
        (compojure [core :only [defroutes GET POST]]
                   [handler :only [site]]
                   [route :only [files not-found]])
        denarius.order
        [denarius.engine :only [insert-order
                                match-order]])
  (:require [org.httpkit.client :as http]
            [compojure.route :as route]))


(def port 8081)

(def book (ref nil))

(defn- wrap-request-logging [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [resp (handler req)]
      (debug (name request-method) (:status resp)
            (if-let [qs (:query-string req)]
              (str uri "?" qs) uri))
      resp)))


(defn on-order-new-limit [req]
  (let [params (:params req)
        [broker-id side-str size price] (for [l [:broker-id
                                                 :side
                                                 :size
                                                 :price]] (l params))
        side      (if (= side-str ":bid") :bid :ask)
        order-ref (create-order-ref (get-order-id) broker-id :limit side (Integer. size) (Integer. price) nil nil)]
    (insert-order @book order-ref)
    (with-channel req channel
      (send! channel {:status 200
                      :headers {"Content-Type" "text/plain"}
                      :body (str "New limit!")}) )))

;(json-str (get-msgs (@clients channel)))   
(defroutes routes
  (POST "/order-new-limit" [] on-order-new-limit)
  (route/not-found "<h1>Page not found</h1>"))

(defn start-http [order-book]
  (dosync (ref-set book @order-book))
  
  (info "Starting server on port" port)
  (run-server (-> #'routes site wrap-request-logging) {:port port})
  ;(run-server async-handler {:port 8081}))
  )