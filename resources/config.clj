{
 :engine {}
 :connector {:database-trades {:class "denarius.connector.db.db_trades_mysql.db-trades-mysql"
                               :options {:host "192.168.0.39" :port "3306" :dbname "denarius" :user "root" :passw ""}}
             :database-orders {:class "denarius.connector.db.db_orders_mem.db-orders-mem"
                               :options nil}
             :database-deskinfo {:class "denarius.connector.db.db_deskinfo_file.db-deskinfo-file"
                                 :options {:file "resources/db_deskinfo_file.json"}}}
 }