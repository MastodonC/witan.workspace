(ns witan.workspace.command
  (:require [taoensso.timbre :as log]
            [witan.workspace.util :as util]
            [witan.workspace.schema :as ws]
            [witan.workspace.protocols :as p]
            [schema.core :as s]
            [schema-contrib.core :as sc]
            [schema.coerce :as coerce]
            [clojure.stacktrace :as st]
            [witan.gateway.schema :as wgs])
  (:use [witan.workspace.workspace]))

(defmethod p/command-processor
  :default
  [c v]
  (let [error {:error (format "Command and version combination is not supported: %s %s" c v)}]
    (reify p/CommandProcessor
      (params [_] error)
      (process [_ params] error))))

#_(defmethod command-processor
    [:workspace/create "1.1"]
    [c v]
    (reify CommandProcessor
      (params [_] {:name s/Str
                   :owner s/Uuid
                   :foo s/Str})
      (process [_ params]
        {:event :workspace/created
         :params (merge params
                        {:id (java.util.UUID/randomUUID)})
         :version "2.0"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- params-schema
  [{:keys [command/key command/version]}]
  (p/params (p/command-processor (keyword key) version)))

(defn- coerce-message
  "Coerce the message into the shape of a command"
  [{:keys [command/key command/params] :as msg}]
  (if-not (re-find #"^workspace" key)
    (log/debug "Observed command but it wasn't intended for us:" key)
    (let [params-schema (params-schema msg)]
      (let [result ((coerce/coercer params-schema coerce/json-coercion-matcher) params)]
        (if (contains? result :error)
          (merge {:error (str "Schema validation failed: " (pr-str (:error result)))} msg)
          (assoc msg :command/params result))))))

(defn- authorise-command
  "Check we have the auth for this command"
  [command]
  command)

(defn- process-command
  "Convert the command into an event"
  [{:keys [command/key command/version command/params command/id] :as msg}]
  (let [result (p/process (p/command-processor (keyword key) version) params)]
    (merge result
           {:id (java.util.UUID/randomUUID)
            :command {:version version
                      :original key
                      :id id}
            :origin "witan.workspace.command"
            :created-at (util/iso-date-time-now)})))

(defn- send-event!
  [event sender]
  (log/info "Sending event:" (:event event))
  (log/debug "Event payload:" event)
  (log/debug "Using sender:" sender)
  (p/send-message! sender :event event))

(defn- send-error!
  [error sender]
  (log/info "Sending error:" (:error error))
  (log/debug "Error payload:" error)
  (log/debug "Using sender:" sender)
  (p/send-message! sender :error error))

;;;;

(defn command-receiver
  [msg event-sender]
  (try
    (when-let [command (coerce-message msg)]
      (if (:command/key command)
        (do
          (log/info "Received command:" (:command/key command))
          #_(log/debug "Command payload (post-coercion):" command))
        (log/warn "Received a bad command:" command))
      (let [event (util/condas-> command c
                                 (not (contains? c :error)) (authorise-command c)
                                 (not (contains? c :error)) (process-command c)
                                 (not (contains? c :error)) (s/validate ws/KafkaEvent c))]
        (if (contains? event :error)
          (do
            (log/error "Command produced an error:" (:error event) msg)
            (send-error! event event-sender))
          (send-event! event event-sender))))
    (catch Exception e (do
                         (log/error "An error occurred:" e)
                         (st/print-stack-trace e)))))
