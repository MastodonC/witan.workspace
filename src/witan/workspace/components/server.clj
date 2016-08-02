(ns witan.workspace.components.server
  (:gen-class)
  (:require [org.httpkit.server           :as httpkit]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [com.stuartsierra.component   :as component]
            [witan.workspace.handler      :refer [app]]
            [taoensso.timbre              :as log]))

(defn wrap-log [handler]
  (fn [request]
    (log/debug "REQUEST:" request)
    (handler request)))

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req ::components components))))

(defrecord HttpKit [port]
  component/Lifecycle
  (start [this]
    (log/info (str "Server started at http://localhost:" port))
    (assoc this :http-kit (httpkit/run-server
                           (-> #'app
                               (wrap-components this)
                               (wrap-content-type "application/json")
                               #_(wrap-cors :access-control-allow-origin [#".*"]
                                            :access-control-allow-methods [:get :post])
                               #_(wrap-log))
                           {:port port})))
  (stop [this]
    (log/info "Stopping server")
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn new-http-server
  [args]
  (map->HttpKit args))
