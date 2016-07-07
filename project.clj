(defproject witan.workspace "0.1.1"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.385"]
                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.4.0-alpha1"]
                 [clj-time "0.11.0"] ; required due to bug in `lein-ring uberwar`
                 [clj-kafka "0.3.4"]
                 [cheshire "5.5.0"]
                 [http-kit "2.1.19"]
                 [cc.qbits/alia "2.12.1" :exclusions [cc.qbits/hayt org.clojure/clojure]]
                 [cc.qbits/hayt "2.1.0"]
                 [metosin/compojure-api "1.0.0" :exclusions [commons-io]]
                 [prismatic/schema "1.1.2"]
                 [kixi/schema-contrib "0.2.0"]
                 [org.onyxplatform/onyx "0.9.4"]
                 [base64-clj "0.1.1"]
                 [aero "1.0.0-beta5"]
                 [joplin.core "0.3.6"]
                 [joplin.cassandra "0.3.6"]
                 [witan.gateway.schema "0.1.0"]
                 [witan.workspace-onyx-plugins "0.1.0" :exclusions [org.onyxplatform/onyx commons-logging]]
                 [com.rpl/specter "0.9.3"]]
  :source-paths ["src"]
  :main witan.workspace.system
  :profiles {:uberjar {:aot  [witan.workspace.system]
                       :main witan.workspace.system
                       :uberjar-name "witan.workspace-standalone.jar"}
             :dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]
                                  [ring/ring-mock "0.3.0"]
                                  [witan.workspace-api "0.1.6"]]
                   :repl-options {:init-ns user}}}
  :repositories [["releases" {:url "https://clojars.org/repo"
                              :creds :gpg}]
                 ["snapshots" {:url "https://clojars.org/repo"
                               :creds :gpg}]])
