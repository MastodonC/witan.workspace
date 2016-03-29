(ns witan.workspace.event
  (:require [taoensso.timbre :as log]
            [witan.workspace.protocols :as p]
            [base64-clj.core :as base64]
            [clojure.stacktrace :as st]))

(defn event-receiver
  [{:keys [event params version id owner origin] :as original} db]
  (log/debug "Received event" original)
  (try
    (let [args {:key event
                :id (java.util.UUID/fromString id)
                :version version
                :creator (java.util.UUID/fromString (or owner (:owner params)))
                :origin origin
                :received_at (java.util.Date.)
                :original_payload (base64/encode (prn-str original))}]
      (log/debug "Saving event as" args)
      (p/insert! db :events args {}))
    (catch Exception e (do
                         (log/error "An error occurred whilst storing the event:" e)
                         (st/print-stack-trace e)))))
