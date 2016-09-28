(ns witan.workspace.components.cassandra
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [qbits.alia                 :as alia]
            [qbits.hayt                 :as hayt]
            [witan.workspace.protocols  :as p]
            [witan.workspace.coercion   :as wc]
            [clojure.java.io            :as io]
            [joplin.repl                :as jrepl :refer [migrate]]))

(defn create-keyspace!
  [host keyspace replication-factor]
  (alia/execute
   (alia/connect (alia/cluster {:contact-points host}))
   (hayt/create-keyspace keyspace
                         (hayt/if-exists false)
                         (hayt/with {:replication
                                     {:class "SimpleStrategy"
                                      :replication_factor replication-factor}}))))
(defn create-connection
  [host keyspace]
  (alia/connect (alia/cluster {:contact-points host}) keyspace))

(defn exec
  [this x]
  (if-let [conn (get this :connection)]
    (try
      (log/trace "Executing" (hayt/->raw x))
      (alia/execute conn x)
      (catch Exception e (log/error "Failed to execute database command:" (str e))))
    (log/error "Unable to execute Cassandra comment - no connection")))

(defrecord Cassandra [host keyspace joplin profile replication-factor]
  p/Database
  (drop-table! [this table]
    (exec this (hayt/drop-table table (hayt/if-exists))))
  (create-table! [this table columns]
    (exec this (hayt/create-table table (hayt/column-definitions columns))))
  (insert! [this table row {:keys [using]}]
    (cond
      using (exec this (hayt/insert table (hayt/values row) (apply hayt/using using)))
      :else (exec this (hayt/insert table (hayt/values row)))))
  (insert! [this table row]
    (p/insert! this table (map wc/hyphen->underscore row) {}))
  (select* [this table where]
    (let [result (exec this (hayt/select table (hayt/where where)))
          reformatted (map wc/underscore->hyphen result)]
      (if (coll? result)
        (map (partial into {}) reformatted)
        reformatted)))
  (select [this table what where]
    (let [result (exec this (hayt/select table (apply hayt/columns (map wc/hyphen->underscore what)) (hayt/where where)))
          reformatted (map wc/underscore->hyphen result)]
      (if (coll? result)
        (map (partial into {}) reformatted)
        reformatted)))

  component/Lifecycle
  (start [component]
    (log/info "Bootstrapping Cassandra...")
    (try
      (do (create-keyspace! host keyspace replication-factor)
          (let [joplin-config (jrepl/load-config (io/resource joplin))]
            (->> profile
                 (migrate joplin-config)
                 (with-out-str)
                 (clojure.string/split-lines)
                 (run! #(log/info "> JOPLIN:" %))))

          (log/info "Connecting to Cassandra..." host keyspace)
          (let [conn (create-connection host keyspace)]
            (assoc component :connection conn)))
      (catch Exception ex
        (do
          (log/error "Failed to start Cassandra:" ex)
          component))))

  (stop [component]
    (log/info "Disconnecting from Cassandra...")
    (dissoc component :connection)))

(defn new-cassandra-connection [args profile]
  (map->Cassandra (assoc args :profile profile)))
