(ns witan.workspace.protocols)

(defprotocol SendMessage
  (send-message! [this topic message]))

;;;;;;;;;

(defprotocol Database
  (drop-table!
    [this table])
  (create-table!
    [this table columns])
  (insert!
    [this table row]
    [this table row args])
  (select*
    [this table where])
  (select
    [this table what where]))

;;;;;;;;;;

(defprotocol Processor
  (params [this])
  (process [this params components]))

(defmulti command-processor
  (fn [command version] [command version]))

(defmulti event-processor
  (fn [command version] [command version]))
