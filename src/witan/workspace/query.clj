(ns witan.workspace.query
  (:require [taoensso.timbre           :as log]
            [schema.core               :as s]
            [witan.workspace.workspace :as w]
            [graph-router.core         :as gr]
            [clj-time.core             :as t]
            [clj-time.format           :as tf]
            ;;
            [witan.workspace-api.protocols :as wp]
            ;;
            [witan.models.dem.ccm.models :as demography]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Cache

(def libraries
  [(demography/model-library)])

(def all-functions
  (vec
   (mapcat
    wp/available-fns libraries)))

(def all-models
  (vec
   (mapcat
    wp/available-models libraries)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Queries

(def lorem-ipsum
  "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Fusce porta consectetur turpis, ac dapibus enim. Quisque volutpat dolor ut molestie luctus. Vivamus sodales neque id lacus laoreet, sit amet commodo neque maximus. Ut nec enim eu orci tristique amet.")

(def bogus-user-id
  #uuid "00000000-0000-0000-0000-000000000000")

(def workspaces
  (atom [{:workspace/name "WSP-One"
          :workspace/id (java.util.UUID/randomUUID)
          :workspace/owner-id bogus-user-id
          :workspace/owner-name "Alice"
          :workspace/modified (t/now)
          :workspace/description lorem-ipsum}
         {:workspace/name "WSP-Two"
          :workspace/id (java.util.UUID/randomUUID)
          :workspace/owner-id bogus-user-id
          :workspace/owner-name "Bob"
          :workspace/modified (t/now)
          :workspace/description lorem-ipsum}
         {:workspace/name "WSP-Three"
          :workspace/id (java.util.UUID/randomUUID)
          :workspace/owner-id bogus-user-id
          :workspace/owner-name "Charlie"
          :workspace/modified (t/now)
          :workspace/description lorem-ipsum}
         {:workspace/name "WSP-Four"
          :workspace/id (java.util.UUID/randomUUID)
          :workspace/owner-id bogus-user-id
          :workspace/owner-name "Denise"
          :workspace/modified (t/now)
          :workspace/description lorem-ipsum}]))

(defn get-available-models
  [_]
  all-models)

(defn get-available-functions
  [_]
  all-functions)

(defn get-workspaces
  []
  @workspaces)

(defn get-workspaces-by-owner
  [_ owner]
  (if (= owner "*")
    (get-workspaces)
    (filter #(= owner (:workspace/owner-id %)) (get-workspaces))))

(defn get-workspace-by-id
  [_ id]
  (some #(when (= id (:workspace/id %)) %) (get-workspaces)))

(defn get-model-by-name-and-version
  [_ name version]
  (some #(when (and (= (:witan/name (:metadata %)) name)
                    (= (:witan/version (:metadata %)) version)) %) all-models))

(defn dt->str
  [k]
  (fn [e]
    (when-let [time (get e k)]
      (tf/unparse (tf/formatters :basic-date-time) time))))

(def function-fields
  [:function/name
   :function/id
   :function/version])

(def workspace-fields
  [:workspace/name
   :workspace/id
   :workspace/owner-name
   :workspace/owner-id
   (gr/with :workspace/modified (dt->str :workspace/modified))
   :workspace/description])

(def model-fields
  [:metadata
   :workflow
   :catalog])

(def graph
  {;; workspaces by owner
   (gr/with :workspace/list-by-owner get-workspaces-by-owner)
   workspace-fields
   ;; workspace by id
   (gr/with :workspace/by-id get-workspace-by-id)
   workspace-fields
   ;;  functions
   (gr/with :workspace/available-functions get-available-functions)
   function-fields
   ;; models
   (gr/with :workspace/available-models get-available-models)
   model-fields
   (gr/with :workspace/model-by-name-and-version get-model-by-name-and-version)
   model-fields})

(defn query
  [query' db]
  (gr/dispatch graph query'))
