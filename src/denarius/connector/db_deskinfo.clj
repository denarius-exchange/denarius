(ns denarius.connector.db-deskinfo)

(defprotocol db-deskinfo
  (init-deskinfo-impl  [this])
  (query-deskinfo-available-funds-impl [this broker-id])
  (query-deskinfo-cantrade-impl [this broker-id initial])
  (stop-deskinfo-impl  [this]))

(defrecord db-deskinfo-nil [dbopt]
  db-deskinfo
  (init-deskinfo-impl  [this] nil)
  (query-deskinfo-available-funds-impl [this broker-id] nil)
  (query-deskinfo-cantrade-impl [this broker-id initial] nil)
  (stop-deskinfo-impl  [this] nil))

; Database name to be set via configuration
(def dbname (atom (db-deskinfo-nil. nil)))

; Methods exposed by the namespace which encapsulate the multimethods and provide
; the dispatching first argument depending on the reference dbname
(defn init-deskinfo [] (init-deskinfo-impl @dbname) )

(defn query-deskinfo-available-funds [broker-id]
  (query-deskinfo-available-funds-impl @dbname broker-id) )

(defn query-deskinfo-cantrade [broker-id initial]
  (query-deskinfo-cantrade-impl @dbname broker-id initial))

(defn stop-deskinfo [] (stop-deskinfo-impl @dbname))


