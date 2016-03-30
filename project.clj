(defproject witan.workspace "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.371"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.3.1"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [clj-kafka "0.3.4"]
                 [cheshire "5.5.0"]
                 [http-kit "2.1.19"]
                 [cc.qbits/alia-all "3.1.3"]
                 [cc.qbits/hayt "3.0.0"]
                 [metosin/compojure-api "1.0.0"]
                 [prismatic/schema "1.0.5"]
                 [kixi/schema-contrib "0.2.0"]
                 [base64-clj "0.1.1"]]
  :uberjar-name "witan.gateway.jar"
  :source-paths ["src"]
  :main witan.workspace.system
  :profiles {:uberjar {:aot  [witan.workspace.server]
                       :main witan.workspace.server}
             :dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [ring/ring-mock "0.3.0"]]
                   :repl-options {:init-ns user}}})
