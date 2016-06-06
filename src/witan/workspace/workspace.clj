(ns witan.workspace.workspace
  (:require [taoensso.timbre           :as log]
            [witan.workspace.protocols :as p]
            [clojure.stacktrace        :as st]))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn by-owner
  [owner fields db]
  (if fields
    (p/select db :workspaces_by_owner fields {:owner owner})
    (p/select* db :workspaces_by_owner {:owner owner})))

(defn by-id
  [id fields db]
  (let [all-events "123"]
    all-events))
