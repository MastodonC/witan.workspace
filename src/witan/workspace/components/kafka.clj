(ns witan.workspace.components.kafka
  (:require [com.stuartsierra.component :as component]
            [witan.workspace.protocols  :as p]
            [taoensso.timbre            :as log]
            [cheshire.core              :as json]
            [clj-kafka.producer         :as kafka]
            [clj-kafka.zk               :as zk]
            [clj-kafka.consumer.zk      :as kafka-zk]
            [clj-kafka.core             :as kafka-core]
            [clojure.core.async         :as async :refer [go-loop chan <! close! put!]]))

(defrecord KafkaProducer [host port]
  p/SendMessage
  (send-message! [component topic raw-message]
    (let [message (json/generate-string raw-message)]
      (if-let [{:keys [connection]} component]
        (if-let [error (kafka/send-message connection (kafka/message (name topic) (.getBytes message)))]
          (log/error "Failed to send message to Kafka:" error)
          (log/debug "Message was sent to Kafka:" topic (count message)))
        (log/error "There is no connection to Kafka."))))

  component/Lifecycle
  (start [component]
    (log/info "Starting Kafka producer...")
    (log/info "Building broker list from ZooKeeper:" host port)
    (let [brokers (zk/brokers {"zookeeper.connect" (str host ":" port)})]
      (if (not-empty brokers)
        (let [broker-string (->>
                             brokers
                             (map (juxt :host :port))
                             (map (partial interpose \:))
                             (map (partial apply str))
                             (interpose \,)
                             (apply str))
              _ (log/info "Kafka broker list:" broker-string)
              connection (kafka/producer {"metadata.broker.list" broker-string
                                          "serializer.class" "kafka.serializer.DefaultEncoder"
                                          "partitioner.class" "kafka.producer.DefaultPartitioner"})]
          (assoc component :connection connection))
        (do
          (log/error "No Kafka brokers were found.")
          (throw (Exception. "No Kafka brokers were found."))))))

  (stop [component]
    (log/info "Stopping Kafka producer...")
    (assoc component :connection nil)))

(defn new-kafka-producer [args]
  (map->KafkaProducer args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn start-listening! [consumer topic receiver receiver-ctx]
  (async/go
    (let [stream (kafka-zk/create-message-stream consumer (name topic))]
      (run! #(receiver (-> %
                           kafka-core/to-clojure
                           :value
                           (String. "UTF-8")
                           (json/parse-string true)) receiver-ctx) stream))))

(defrecord KafkaConsumer [host port topic receiver receiver-ctx group-id]
  component/Lifecycle
  (start [component]
    (log/info "Starting Kafka consumer..." host port topic)
    (let [config {"zookeeper.connect" (str host ":" port)
                  "group.id" group-id
                  "auto.offset.reset" "smallest"
                  "auto.commit.enable" "true"}
          consumer (kafka-zk/consumer config)]
      (start-listening! consumer topic receiver (select-keys component receiver-ctx))
      (assoc component :consumer consumer)))

  (stop [component]
    (log/info "Stopping Kafka consumer...")
    (when-let [consumer (:consumer component)]
      (kafka-zk/shutdown consumer))
    (assoc component :consumer nil)))

(defn new-kafka-consumer [args]
  (map->KafkaConsumer args))
