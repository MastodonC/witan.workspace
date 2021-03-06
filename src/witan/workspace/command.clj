(ns witan.workspace.command
  (:require [taoensso.timbre            :as log]
            [witan.workspace.util       :as util]
            [witan.workspace.schema     :as ws]
            [witan.workspace.protocols  :as p]
            [schema.core                :as s]
            [schema-contrib.core        :as sc]
            [schema.coerce              :as coerce]
            [clojure.stacktrace         :as st]
            [witan.gateway.schema       :as wgs]
            [witan.workspace-api.schema :as was]
            [witan.workspace.coercion   :as wc]
            [witan.workspace.event      :as ev])
  (:use [witan.workspace.workspace]))

(defmethod p/command-processor
  :default
  [c v]
  (let [error {:error (format "Command and version combination is not supported: %s %s" c v)}]
    (reify p/Processor
      (params [_] error)
      (process [_ _ _] error))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- params-schema
  [{:keys [command/key command/version]}]
  (p/params (p/command-processor (keyword key) version)))

(defn- coerce-message
  "Coerce the message into something usable"
  [{:keys [command/key] :as msg}]
  (if-not (and
           (= (:message/type msg) "command-processed")
           (re-find #"^workspace" key))
    (log/debug "Observed command but it wasn't intended for us:" key (:message/type msg))
    (let [result ((coerce/coercer (get wgs/CommandProcessed "1.0.0") coerce/json-coercion-matcher) msg)]
      (if (contains? result :error)
        (merge {:error (str "Command message schema coercion failed: " (pr-str (:error result)))} msg)
        result))))

(defn- coerce-params
  "Coerce the params of message into the required format"
  [{:keys [command/params] :as msg}]
  (let [params-schema' (params-schema msg)]
    (if (contains? params-schema' :error)
      params-schema'
      (let [result ((coerce/coercer params-schema' wc/param-coercion-matcher) params)]
        (if (contains? result :error)
          (merge {:error (str "Command parameter schema coercion failed: " (pr-str (:error result)))} msg)
          (assoc msg  :command/params  result))))))

(defn- authorise-command
  "Check we have the auth for this command"
  [command]
  command)

(defn- process-command
  "Convert the command into an event"
  [{:keys [command/key command/version command/params command/id command/receipt]
    :as msg}]
  (let [result (p/process (p/command-processor (keyword key) version) params nil)]
    (ev/create-event receipt result)))

;;;;

(defn command-receiver
  [msg {event-sender :kp}]
  (try
    (let [command (coerce-message msg)]
      (if (and (not (contains? command :error))
               (:command/key command))
        (do
          (wgs/validate-message "1.0.0" :command-processed command)
          (log/info "Received command:" (:command/key command))
          (let [event (util/condas-> command c
                                     (not (contains? c :error)) (coerce-params c)
                                     (not (contains? c :error)) (authorise-command c)
                                     (not (contains? c :error)) (process-command c)
                                     (not (contains? c :error)) (wgs/validate-message
                                                                 "1.0.0"
                                                                 :event
                                                                 c))]
            (if (contains? event :error)
              (do
                (log/error "Command produced an error:" (:error event) command)
                (ev/send-event! event-sender (ev/create-event
                                              (:command/receipt command)
                                              {:event/params (merge event {:original msg})
                                               :event/version "1.0.0"
                                               :event/key (-> command
                                                              :command/key
                                                              (str)
                                                              (subs 1)
                                                              (str "-failed")
                                                              (keyword))})))
              (ev/send-event! event-sender event))))
        (log/warn "Received a bad command:" command)))
    (catch Exception e (do
                         (log/error "An error occurred:" e)
                         (st/print-stack-trace e)))))
