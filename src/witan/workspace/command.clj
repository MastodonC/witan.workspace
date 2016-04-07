(ns witan.workspace.command
  (:require [taoensso.timbre :as log]
            [witan.workspace.util :as util]
            [witan.workspace.schema :as ws]
            [witan.workspace.protocols :as p]
            [schema.core :as s]
            [schema-contrib.core :as sc]
            [schema.coerce :as coerce]
            [clojure.stacktrace :as st]))

(defprotocol CommandProcessor
  (params [this])
  (process [this params]))

(defmulti command-processor
  (fn [command version] [command version]))

(defmethod command-processor
  :default
  [c v]
  (let [error {:error (format "Command and version combination is not supported: %s %s" c v)}]
    (reify CommandProcessor
      (params [_] error)
      (process [_ params] error))))

(defmethod command-processor
  [:workspace/create "1.0"]
  [c v]
  (reify CommandProcessor
    (params [_] {:name s/Str
                 :owner s/Uuid})
    (process [_ params]
      {:event :workspace/created
       :params (merge params
                      {:id (java.util.UUID/randomUUID)})
       :version "1.0"})))

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

(defn- command-schema
  [msg]
  (let [{:keys [command version]} msg
        command (keyword command)
        params (params (command-processor command version))
        result (if (contains? params :error) params {:params params})]
    (merge {:command s/Keyword
            :id      s/Str
            :version (s/pred util/version?)}
           result
           ws/KafkaCommandBase)))

(defn- coerce-message
  "Coerce the message into the shape of a command"
  [{:keys [command] :as msg}]
  (if-not (re-find #"^workspace" command)
    (log/debug "Observed command but it wasn't intended for us:" command)
    (let [schema (command-schema msg)]
      (if (contains? schema :error)
        (merge schema msg)
        (let [result ((coerce/coercer schema coerce/json-coercion-matcher) msg)]
          (if (contains? result :error)
            (merge {:error (str "Schema validation failed: " (prn-str (:error result)))} msg)
            result))))))

(defn- authorise-command
  "Check we have the auth for this command"
  [command]
  command)

(defn- process-command
  "Convert the command into an event"
  [{:keys [command version params id] :as msg}]
  (let [result (process (command-processor command version) params)]
    (merge result
           {:id (java.util.UUID/randomUUID)
            :command {:version version
                      :original command
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
      (if (:command command)
        (do
          (log/info "Received command:" (:command command))
          (log/debug "Command payload (post-coercion):" command))
        (log/warn "Received a bad command:" command))
      (let [event (util/condas-> command c
                                 (not (contains? c :error)) (authorise-command c)
                                 (not (contains? c :error)) (process-command c)
                                 (not (contains? c :error)) (s/validate ws/KafkaEvent c) )]
        (if (contains? event :error)
          (do
            (log/error "Command produced an error:" (:error event) msg)
            (send-error! event event-sender))
          (send-event! event event-sender))))
    (catch Exception e (do
                         (log/error "An error occurred:" e)
                         (st/print-stack-trace e)))))
