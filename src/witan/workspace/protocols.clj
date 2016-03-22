(ns witan.workspace.protocols)

(defprotocol SendMessage
  (send-message! [this topic message]))
