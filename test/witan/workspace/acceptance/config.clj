(ns witan.workspace.acceptance.config)

(def config
  {:env-config {:onyx/tenancy-id "testcluster"
                :onyx.bookkeeper/server? false
                :zookeeper/address "127.0.0.1:2188"
                :zookeeper/server? true
                :zookeeper.server/port 2188}
   :peer-config {:onyx/tenancy-id                       "testcluster"
                 :zookeeper/address                     "127.0.0.1:2188"
                 :onyx.peer/job-scheduler               :onyx.job-scheduler/greedy
                 :onyx.peer/zookeeper-timeout           60000
                 :onyx.messaging/allow-short-circuit?   false
                 :onyx.messaging/impl                   :aeron
                 :onyx.messaging/bind-addr              "localhost"
                 :onyx.messaging/peer-port              40200
                 :onyx.messaging.aeron/embedded-driver?  true
                 :onyx.messaging/backpressure-strategy :high-restart-latency}
   :redis-config {:redis/uri "redis://localhost:6379"}
   :batch-settings {:onyx/batch-size 1
                    :onyx/batch-timeout 1000}})