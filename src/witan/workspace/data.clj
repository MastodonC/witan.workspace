(ns witan.workspace.data
  (:require [schema.core                 :as s]
            [schema.coerce               :as coerce]
            [clojure.core.matrix.dataset :as ds]))

(defn- custom-keyword [coll]
  (mapv #(-> %
             (clojure.string/replace #"[. /']" "-")
             keyword) coll))

(defn make-row-schema
  [col-schema]
  (mapv (fn [s] (let [datatype (-> s :schema first)
                      fieldname (:name s)]
                  (s/one datatype fieldname)))
        (:columns col-schema)))

(defn make-col-names-schema
  [col-schema]
  (mapv (fn [s] (let [datatype (:schema s)
                      fieldname (:name s)]
                  (s/one datatype fieldname)))
        (:column-names col-schema)))

(defn record-coercion
  "Coerce numbers by matching them to the
    type specified in the schema"
  [schema data]
  (let [coerce-data-fn
        (coerce/coercer schema
                        coerce/string-coercion-matcher)]
    (coerce-data-fn data)))

(defn apply-row-schema
  [col-schema data]
  (let [row-schema (make-row-schema col-schema)]
    (map #((partial (fn [s r] (record-coercion s r)) row-schema) %) data)))

(defn apply-col-names-schema
  [col-schema headers]
  (let [col-names-schema (make-col-names-schema col-schema)]
    (record-coercion col-names-schema headers)))

(defn create-dataset-after-coercion
  [{:keys [column-names columns]}]
  (ds/dataset column-names columns))

(defn ->dataset
  [headers data schema]
  (create-dataset-after-coercion
   {:column-names (apply-col-names-schema schema (custom-keyword headers))
    :columns (vec (apply-row-schema schema data))}))
