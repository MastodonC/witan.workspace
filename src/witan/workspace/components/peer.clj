(ns witan.workspace.components.peer
  (:gen-class)
  (:require [org.httpkit.client           :as http]
            [com.stuartsierra.component   :as component]
            [taoensso.timbre              :as log]))

(defn functions [component]
  (str "foobar"))

(defrecord PeerHandler [host port]
  component/Lifecycle
  (start [component]
    (log/info "Starting peer handler" host port)
    component)
  (stop [component]
    (log/info "Stopping peer handler")
    component))

(defn new-peer-handler
  [args]
  (map->PeerHandler args))
