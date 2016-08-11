(ns witan.workspace.workspace
  (:require [taoensso.timbre            :as log]
            [witan.workspace.protocols  :as p]
            [clojure.stacktrace         :as st]
            [witan.gateway.schema       :as wgs]
            [witan.workspace-api.schema :as was]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands

(defmethod p/command-processor
  [:workspace/save "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] {:workspace/to-save (get wgs/WorkspaceMessage "1.0.0")})
    (process [_ {:keys [workspace/to-save]} _]
      (log/debug "SAVING WORKSPACE" to-save)
      {:event/key :workspace/saved
       :event/version "1.0.0"
       :event/params to-save})))

(defmethod p/command-processor
  [:workspace/run "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] {:workspace/to-run (-> (get wgs/WorkspaceMessage "1.0.0")
                                       (dissoc #schema.core.OptionalKey{:k :workspace/workflow}
                                               #schema.core.OptionalKey{:k :workspace/catalog})
                                       (assoc :workspace/workflow (:workflow was/Workspace)
                                              :workspace/catalog  (:catalog was/Workspace)))})
    (process [_ {:keys [workspace/to-run]} _]
      (log/debug "RUNNING WORKSPACE" to-run)
      {:event/key :workspace/started-running
       :event/version "1.0.0"
       :event/params to-run})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events

(defmethod p/event-processor
  [:workspace/saved "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] (get wgs/WorkspaceMessage "1.0.0"))
    (process [_ wsp {:keys [db]}]
      (log/debug "Saving workspace..." wsp))))

(defmethod p/event-processor
  [:workspace/started-running "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] (get wgs/WorkspaceMessage "1.0.0"))
    (process [_ wsp {:keys [db]}]
      (log/debug "Running workspace..." db))))
