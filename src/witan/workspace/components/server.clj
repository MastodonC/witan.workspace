(ns witan.workspace.components.server
  (:gen-class)
  (:require [org.httpkit.server           :as httpkit]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [compojure.api.middleware     :refer [wrap-components]]
            [com.stuartsierra.component   :as component]
            [witan.workspace.handler      :refer [app]]
            [taoensso.timbre              :as log]))

(defrecord HttpKit []
  component/Lifecycle
  (start [this]
    (log/info "Server started at http://localhost:3001")
    (assoc this :http-kit (httpkit/run-server
                           (-> #'app
                               (wrap-components this)
                               (wrap-content-type "application/json"))
                           {:port 3001})))
  (stop [this]
    (log/info "Stopping server")
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn new-http-server
  []
  (->HttpKit))
