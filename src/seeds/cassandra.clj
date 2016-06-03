(ns seeds.cassandra
  (:require [qbits.alia                 :as alia]
            [qbits.hayt                 :as hayt]
            [joplin.cassandra.database :refer [get-connection]]))

(defn run [target & args]
  (let [conn (get-connection (-> target :db :hosts)
                             (-> target :db :keyspace))]
    #_(alia/execute conn
                    (hayt/insert "users" (hayt/values {:id "Kalle" :email "ole@dole.doff"})))))
