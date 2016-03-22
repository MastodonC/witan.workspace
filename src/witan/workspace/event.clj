(ns witan.workspace.event
  (:require [taoensso.timbre :as log]))

(defn event-receiver
  [msg _]
  (log/info "GOT EVENT" msg))
