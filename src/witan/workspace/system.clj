(ns witan.workspace.system
  (:require [com.stuartsierra.component        :as component]
            [taoensso.timbre                   :as timbre]
            [aero.core                         :refer [read-config]]
            [witan.workspace.logstash-appender :as logstash]
            [witan.workspace.protocols         :as p]
            ;;
            [witan.workspace.components.kafka     :refer [new-kafka-consumer
                                                          new-kafka-producer]]
            [witan.workspace.components.cassandra :refer [new-cassandra-connection]]
            [witan.workspace.components.server    :refer [new-http-server]]
            [witan.workspace.command              :refer [command-receiver]]
            [witan.workspace.event                :refer [event-receiver]])
  (:gen-class))

(defn new-system
  ([profile]
   (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})]

     ;; logging config
     (timbre/merge-config!
      (assoc (:log config)
             :output-fn (partial logstash/output-fn {:stacktrace-fonts {}})
             :timestamp-opts logstash/logback-timestamp-opts))

     ;; create system
     (component/system-map
      :db                      (new-cassandra-connection (:cassandra config) profile)
      :server                  (component/using
                                (new-http-server (:webserver config)) [:db])
      :kafka-producer          (new-kafka-producer (-> config :kafka :zk))

      :kafka-consumer-commands (component/using
                                (new-kafka-consumer (merge {:topic    :command
                                                            :group-id "witan.workspace.consumer"
                                                            :receiver command-receiver
                                                            :receiver-ctx [:kp]} (-> config :kafka :zk)))
                                {:kp :kafka-producer})

      :kafka-consumer-events   (component/using
                                (new-kafka-consumer (merge {:topic :event
                                                            :group-id "witan.workspace.consumer"
                                                            :receiver event-receiver
                                                            :receiver-ctx [:kp :db]} (-> config :kafka :zk)))
                                {:kp :kafka-producer
                                 :db :db})))))

(defn -main [& [arg]]
  (let [profile (or (keyword arg) :production)]

    ;; https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread ex]
         (timbre/error "Unhandled exception:" ex))))

    (component/start
     (new-system profile))))
