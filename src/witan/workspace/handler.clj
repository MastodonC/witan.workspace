(ns witan.workspace.handler
  (:require [compojure.api.sweet             :refer :all]
            [ring.util.http-response         :refer :all]
            [taoensso.timbre                 :as log]
            [schema.core                     :as s]
            [witan.workspace.workspace       :as w]
            [witan.workspace.components.peer :as p]))

(defn params-vector ;; this probably should be middleare
  [req k]
  (let [x (-> req :params k)]
    (if-not (map? x) nil
            (vec (vals x)))))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Witan Workspace Query service"
                   :description ""}
            :tags [{:name "api", :description "some apis"}]}}}

   (GET "/by-owner" req
     :summary "Queries the workspaces by owner"
     :components [db]
     :query-params [owner :- s/Uuid]
     (do
       (log/debug req)
       (w/by-owner owner (params-vector req :fields) db)))

   (GET "/by-id" req
     :summary "Queries the workspaces by id"
     :components [db]
     :query-params [id :- s/Uuid]
     (w/by-id id (params-vector req :fields) db))

   (GET "/functions" req
     :summary "Returns a list of functions available"
     :components [peer]
     :query-params []
     (p/functions peer))

   (GET "/models" req
     :summary "Returns a list of models available"
     :components [peer]
     :query-params []
     (p/models peer))

   (GET "/predicates" req
     :summary "Returns a list of predicates available"
     :components [peer]
     :query-params []
     (p/predicates peer))))
