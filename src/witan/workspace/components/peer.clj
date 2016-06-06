(ns witan.workspace.components.peer
  (:gen-class)
  (:require [org.httpkit.client         :as http]
            [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]))

(defn- peer-request
  [method {:keys [host port]}]
  (let [resp @(http/get (format "http://%s:%d/%s" host port method))]
    (assoc
     (select-keys resp [:status :body])
     :headers {"Content-Type" "application/json"})))

(def functions
  (partial peer-request "functions"))

(def models
  (partial peer-request "models"))

(def predicates
  (partial peer-request "predicates"))

(defrecord PeerHandler [host port]
  component/Lifecycle
  (start [component]
    (log/info "Starting peer handler" host port)
    component))

(defn new-peer-handler
  [args]
  (map->PeerHandler args))
