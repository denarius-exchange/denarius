{
 :engine {}
 :connector {:database-trades {:class "denarius.connector.db.db_trades_mem.db-trades-mem"
                               :options nil}
             :database-orders {:class "denarius.connector.db.db_orders_mem.db-orders-mem"
                               :options nil}
             :database-deskinfo {:class "denarius.connector.db.db_deskinfo_mem.db-deskinfo-mem"
                                 :options "resources/db_deskinfo_file.json"}}
 }