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

(defn apply-row-schema
  [col-schema data]
  (let [row-schema (make-row-schema col-schema)]
    (map (coerce/coercer row-schema coerce/string-coercion-matcher) data)))

(defn apply-col-names-schema
  [col-schema headers]
  (let [col-names-schema (make-col-names-schema col-schema)]
    ((coerce/coercer col-names-schema coerce/string-coercion-matcher) headers)))

(defn ->dataset
  [headers data schema]
  (ds/dataset
   (apply-col-names-schema schema (custom-keyword headers))
   (vec (apply-row-schema schema data))))
