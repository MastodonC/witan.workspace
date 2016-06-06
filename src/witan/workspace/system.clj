(ns witan.workspace.system
  (:require [com.stuartsierra.component        :as component]
            [taoensso.timbre                   :as timbre]
            [aero.core                         :refer [read-config]]
            [witan.workspace.logstash-appender :as logstash]
            ;;
            [witan.workspace.components.kafka     :refer [new-kafka-consumer
                                                          new-kafka-producer]]
            [witan.workspace.components.cassandra :refer [new-cassandra-connection]]
            [witan.workspace.components.server    :refer [new-http-server]]
            [witan.workspace.components.peer      :refer [new-peer-handler]]
            [witan.workspace.command              :refer [command-receiver]]
            [witan.workspace.event                :refer [event-receiver]])
  (:gen-class))

(defn new-system
  ([profile]
   (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})]

     ;; logging config
     (if (= profile :production)
       (timbre/merge-config! (assoc (:log config) :output-fn logstash/output-fn))
       (timbre/merge-config! (:log config)))

     ;; create system
     (component/system-map
      :db                      (new-cassandra-connection (:cassandra config) profile)
      :peer                    (new-peer-handler (:peer config))
      :server                  (component/using
                                (new-http-server (:webserver config)) [:db :peer])
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
                                {:receiver-ctx :db})))))

(defn -main [& args]
  (component/start
   (new-system :production)))
