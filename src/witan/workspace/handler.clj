(ns witan.workspace.handler
  (:require [compojure.api.sweet       :refer :all]
            [ring.util.http-response   :refer :all]
            [taoensso.timbre           :as log]
            [schema.core               :as s]
            [witan.workspace.protocols :as p]))

(defn get-events-by-id
  [id db]
  (p/select db :events [:key :params] {:id id}))

(defn by-owner
  [owner db]
  (let [creation-events (p/select db :events [:id] {:key "workspace/created"})]
    (ok (map :id creation-events))))

(defn by-id
  [id fields db]
  (let [all-events (get-events-by-id id db)]
    (ok all-events)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
        :components [db]
        :query-params [owner :- s/Uuid]
        :return [s/Uuid]
        (by-owner owner db))

   (GET "/by-id" []
        :summary "Just a test"
        :components [db]
        :query-params [id :- s/Uuid
                       {fields :- s/Str nil}]
        (by-id id fields db))))
