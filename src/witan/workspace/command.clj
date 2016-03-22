(ns witan.workspace.command
  (:require [taoensso.timbre :as log]
            [witan.workspace.util :as util]
            [witan.workspace.schema :as ws]
            [witan.workspace.protocols :as p]
            [schema.core :as s]
            [schema-contrib.core :as sc]
            [schema.coerce :as coerce]))

(defprotocol CommandProcessor
  (params [this])
  (process [this params]))

(defmulti command-processor
  (fn [command version] [command version]))

(defmethod command-processor
  [:workspace/create "1.0"]
  [c v]
  (reify CommandProcessor
    (params [_] {:name s/Str
                 :owner s/Str})
    (process [_ params]
      {:event :workspace/created
       :params (merge params
                      {:id (java.util.UUID/randomUUID)})
       :version "1.0"})))

(defmethod command-processor
  [:workspace/create "1.1"]
  [c v]
  (reify CommandProcessor
    (params [_] {:name s/Str
                 :owner s/Str
                 :foo s/Str})
    (process [_ params]
      {:event :workspace/created
       :params (merge params
                      {:id (java.util.UUID/randomUUID)})
       :version "2.0"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn command-schema
  [msg]
  (let [{:keys [command version]} msg
        command (keyword command)]
    (merge {:command s/Keyword
            :id      s/Str
            :version (s/pred util/version?)
            :params (params (command-processor command version))}
           ws/KafkaCommandBase)))

(defn- coerce-message
  "Coerce the message into the shape of a command"
  [{:keys [command] :as msg}]
  (if-not (re-find #"^workspace" command)
    (log/debug "Observed command but it wasn't intended for us:" command)
    ((coerce/coercer (command-schema msg) coerce/json-coercion-matcher) msg)))

(defn- authorise-command
  "Check we have the auth for this command"
  [command]
  command)

(defn- process-command
  "Convert the command into an event"
  [{:keys [command version params id] :as msg}]
  (merge (process (command-processor command version) params)
         {:id (java.util.UUID/randomUUID)
          :command {:version version
                    :original command
                    :id id}
          :origin "witan.workspace.command"
          :created-at (util/iso-date-time-now)}))

(defn send-event!
  [event sender]
  (log/info "Sending event:" (:event event))
  (log/debug "Event payload:" event)
  (log/debug "Using sender:" sender)
  (p/send-message! sender :event event))

(defn command-receiver
  [msg event-sender]
  (try
    (when-let [command (-> msg
                           (util/json->clojure)
                           (coerce-message))]
      (log/info "Received command:" (:command command))
      (log/debug "Command payload:" command)
      (let [event (util/condas-> command c
                                 (not (contains? c :error)) (authorise-command c)
                                 (not (contains? c :error)) (process-command c)
                                 true                       (s/validate ws/KafkaEvent c) )]
        (if (contains? event :error)
          (do
            (log/error "Command produced an error:" event)
            (log/error "From:" msg))
          (send-event! event event-sender))))
    (catch Exception e (log/error "An error occurred:" (.getMessage e)))))
