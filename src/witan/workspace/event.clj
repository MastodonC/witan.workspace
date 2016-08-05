(ns witan.workspace.event
  (:require [taoensso.timbre :as log]
            [witan.workspace.protocols :as p]
            [base64-clj.core :as base64]
            [clojure.stacktrace :as st]
            [schema.coerce :as coerce]
            [witan.workspace.util :as u]
            [witan.workspace.workspace :as w]
            [witan.gateway.schema :as wgs]
            [witan.workspace-api.schema :as was]))

(defn store-event!
  [event db]
  (try
    (let [args {:key (subs (str (:event/key event)) 1)
                :id (:event/id event)
                :version (:event/version event)
                :origin (:event/origin event)
                :received_at (java.util.Date.)
                :original_payload (base64/encode (prn-str event))}]
      (log/debug "Saving event" (:event/key event) (:event/version event))
      (p/insert! db :events args))
    (catch Exception e (do
                         (log/error "An error occurred whilst storing the event:" e)
                         (st/print-stack-trace e)))))

;; -------------------------------------------------------------

(defmethod p/event-processor
  :default
  [c v]
  (let [error {:error (format "Event and version combination is not supported: %s %s" c v)}]
    (reify p/Processor
      (params [_] error)
      (process [_ _ _] (log/warn error)))))

(defn- params-schema
  [{:keys [event/key event/version]}]
  (p/params (p/event-processor (keyword key) version)))

(defn- coerce-params
  "Coerce the params of message into the required format"
  [{:keys [event/params] :as msg}]
  (let [params-schema' (params-schema msg)]
    (if (contains? params-schema' :error)
      params-schema'
      (let [result ((coerce/coercer params-schema' u/param-coercion-matcher) params)]
        (if (contains? result :error)
          (merge {:error (str "Event parameter schema coercion failed: " (pr-str (:error result)))} msg)
          (assoc msg :event/params result))))))

(defn- process-event!
  "Convert the event"
  [{:keys [event/key event/version event/params]
    :as msg} db]
  (p/process (p/event-processor (keyword key) version) params {:db db}))

(defn event-receiver
  [original db]
  (try
    (let [event ((coerce/coercer (get wgs/Event "1.0.0") coerce/json-coercion-matcher) original)]
      (if (and (not (contains? event :error))
               (:event/key event))
        (let [_ (wgs/validate-message "1.0.0" :event event)
              event (coerce-params event)]
          (if (not (contains? event :error))
            (do (log/debug "Received event" (select-keys event [:event/key :event/version :event/origin]))
                (store-event! event db)
                (process-event! event db))
            (log/error "Event produced an error:" event)))
        (log/warn "Received a bad event:" event)))
    (catch Throwable e
      (log/error "Failed to process event:" e "\n" original))))
