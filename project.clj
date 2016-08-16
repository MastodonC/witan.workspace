(defproject witan.workspace "0.1.2-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.4.0-alpha1"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [clj-kafka "0.3.4"]
                 [cheshire "5.5.0"]
                 [http-kit "2.1.19"]
                 [cc.qbits/alia "2.12.1" :exclusions [cc.qbits/hayt org.clojure/clojure]]
                 [cc.qbits/hayt "2.1.0"]
                 [compojure "1.5.1"]
                 [prismatic/schema "1.1.2"]
                 [kixi/schema-contrib "0.2.0"]
                 [base64-clj "0.1.1"]
                 [aero "1.0.0-beta5"]
                 [joplin.core "0.3.6"]
                 [joplin.cassandra "0.3.6"]
                 [com.cognitect/transit-clj "0.8.288"]
                 [com.outpace/schema-transit "0.2.3"]
                 [lockedon/graph-router "0.1.7"]
                 [org.clojure/data.csv "0.1.3"]
                 [net.mikera/core.matrix "0.52.2"]
                 ;;
                 [commons-logging/commons-logging "1.2"]
                 [com.fasterxml.jackson.core/jackson-annotations "2.6.3"]
                 [com.fasterxml.jackson.core/jackson-core "2.6.3"]
                 [amazonica "0.3.73" :exclusions [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor
                                                  com.google.guava/guava
                                                  commons-logging
                                                  commons-codec
                                                  com.fasterxml.jackson.core/jackson-core]]
                 ;;
                 [witan.gateway.schema "0.1.1"]
                 [witan.workspace-api "0.1.19"]
                 [witan.workspace-executor "0.2.4"]
                 ;;
                 [witan.models.demography "0.1.0"]]
  :source-paths ["src"]
  :profiles {:uberjar {:aot  [witan.workspace.system]
                       :main witan.workspace.system
                       :uberjar-name "witan.workspace-standalone.jar"}
             :dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [ring/ring-mock "0.3.0"]]
                   :repl-options {:init-ns user}}}
  :repositories [["releases" {:url "https://clojars.org/repo"
                              :creds :gpg}]
                 ["snapshots" {:url "https://clojars.org/repo"
                               :creds :gpg}]])
