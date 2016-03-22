(ns witan.workspace.system
  (:require [com.stuartsierra.component :as component]
            ;;
            [witan.workspace.components.kafka :refer [new-kafka-consumer
                                                      new-kafka-producer]]
            [witan.workspace.command          :refer [command-receiver]]
            [witan.workspace.event            :refer [event-receiver]]))

(defn new-system
  []
  (let [config {:kafka {:zk {:host "127.0.0.1"
                             :port 2181}}}]
    (component/system-map
     :kafka-producer          (new-kafka-producer (-> config :kafka :zk))

     :kafka-consumer-commands (component/using
                               (new-kafka-consumer (merge {:topic    :command
                                                           :receiver command-receiver} (-> config :kafka :zk)))
                               {:receiver-ctx :kafka-producer})

     :kafka-consumer-events   (new-kafka-consumer (merge {:topic :event
                                                          :receiver event-receiver} (-> config :kafka :zk))))))
