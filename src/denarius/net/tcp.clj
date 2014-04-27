(ns denarius.net.tcp)

(def message-request-order     2)
(def message-request-position  3)
(def message-request-list      4)
(def message-request-cancel    5)
(def message-request-trades    6)
(def message-response-received 0)
(def message-response-error    1)
(def message-response-executed 2)
(def message-response-position 3)
(def message-response-list     4)
(def message-response-cancel   5)
(def message-response-trades   6)


(def default-engine-port    7891)
(def default-connector-port 7892)