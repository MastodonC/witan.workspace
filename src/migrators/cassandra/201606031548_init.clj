(ns migrators.cassandra.201606031548-init
  (:use [joplin.cassandra.database])
  (:require [qbits.alia :as alia]
            [qbits.hayt :as hayt]))

(defn up [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    ;; EVENTS
    (alia/execute
     conn
     (hayt/create-table
      "events"
      (hayt/column-definitions {:key              :text
                                :id               :uuid
                                :event_id         :uuid
                                :event_version    :text
                                :creator           :uuid
                                :origin           :text
                                :received_at      :timestamp
                                :original_payload :text
                                ;;
                                :primary-key  [:id :received_at :key]})
      (hayt/with {:clustering-order [[:received_at :asc]]})))

    ;; WORKSPACES
    (alia/execute
     conn
     (hayt/create-table
      "workspaces_by_id"
      (hayt/column-definitions {:id               :uuid
                                :owner            :uuid
                                :name             :text
                                :created_at       :timestamp
                                :original_payload :text
                                ;;
                                :primary-key  [:id]})))

    (alia/execute
     conn
     (hayt/create-table
      "workspaces_by_owner"
      (hayt/column-definitions {:id               :uuid
                                :owner            :uuid
                                :name             :text
                                :created_at       :timestamp
                                :original_payload :text
                                ;;
                                :primary-key  [:owner]})))))

(defn down [db]
  (let [conn (get-connection (:hosts db) (:keyspace db))]
    ;; EVENTS
    (alia/execute conn (hayt/drop-table "events"))
    ;; WORKSPACES
    (alia/execute conn (hayt/drop-table "workspaces_by_id"))
    (alia/execute conn (hayt/drop-table "workspaces_by_owner"))))
