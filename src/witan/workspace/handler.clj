(ns witan.workspace.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :as log]
            [schema.core :as s]))
(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Witan Workspace Query service"
                   :description ""}
            :tags [{:name "api", :description "some apis"}]}}}

   (GET "/by-owner" []
        :summary "Just a test"
        :query-params [owner :- s/Uuid]
        (ok "hello from workspace"))

   (GET "/by-id" []
        :summary "Just a test"
        :query-params [id :- s/Uuid]
        (ok "hello from workspace"))))
