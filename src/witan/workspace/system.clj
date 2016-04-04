(ns witan.workspace.system
  (:require [com.stuartsierra.component :as component]
            ;;
            [witan.workspace.components.kafka :refer [new-kafka-consumer
                                                      new-kafka-producer]]
            [witan.workspace.components.cassandra :refer [new-cassandra-connection]]
            [witan.workspace.components.server :refer [new-http-server]]
            [witan.workspace.command          :refer [command-receiver]]
            [witan.workspace.event            :refer [event-receiver]]))

(defn new-system
  []
  (let [config {:kafka {:zk {:host "127.0.0.1"
                             :port 2181}}

                :cassandra {:host "127.0.0.1"
                            :keyspace "witan_workspace"}}]
    (component/system-map
     :db                      (new-cassandra-connection (-> config :cassandra))
     :server                  (component/using
                               (new-http-server) [:db])
     :kafka-producer          (new-kafka-producer (-> config :kafka :zk))

     :kafka-consumer-commands (component/using
                               (new-kafka-consumer (merge {:topic    :command
                                                           :group-id "witan.workspace.consumer"
                                                           :receiver command-receiver} (-> config :kafka :zk)))
                               {:receiver-ctx :kafka-producer})

     :kafka-consumer-events   (component/using
                               (new-kafka-consumer (merge {:topic :event
                                                           :group-id "witan.workspace.consumer"
                                                           :receiver event-receiver} (-> config :kafka :zk)))
                               {:receiver-ctx :db}))))

(defn -main [& args]
  (component/start
   (new-system)))
