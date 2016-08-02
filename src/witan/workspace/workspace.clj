(ns witan.workspace.workspace
  (:require [taoensso.timbre           :as log]
            [witan.workspace.protocols :as p]
            [clojure.stacktrace        :as st]
            [witan.gateway.schema      :as wgs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands

(defmethod p/command-processor
  [:workspace/save "1.0"]
  [c v]
  (reify p/CommandProcessor
    (params [_] {:workspace/to-save (get wgs/Workspace "1.0")})
    (process [_ params]
      (log/debug "SAVING WORKSPACE" params)
      {:event :workspace/saved
       :params (merge params
                      {:id (java.util.UUID/randomUUID)})
       :version "1.0"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events

(defmulti process-event!
  (fn [{:keys [event version]} _] [(keyword event) version]))

(defmethod process-event!
  [:workspace/created "1.0"]
  [{:keys [params]} db]
  (let [{:keys [name owner id]} params
        tbls [:workspaces_by_id :workspaces_by_owner]
        args {:id (java.util.UUID/fromString id)
              :owner (java.util.UUID/fromString owner)
              :name name
              :created_at (java.util.Date.)
              :last_updated (java.util.Date.)}]
    (run! #(p/insert! db % args) tbls)))
