(ns witan.workspace.coercion
  (:require [schema.coerce              :as coerce]
            [witan.workspace-api.schema :as was]))

(defn uuid-coercion
  [schema]
  (when (= schema java.util.UUID)
    (fn [x]
      (java.util.UUID/fromString x))))

(defn workflow-node-coercion
  [schema]
  (when (= schema was/WorkflowNode)
    (fn [x]
      (cond
        (and
         (vector? x)
         (every? string? x)) (mapv keyword x)
        (and
         (vector? x)
         (vector? (last x))) (-> x
                                 (update 0 keyword)
                                 (update 1 (partial mapv keyword)))
        :else nil))))

(defn param-coercion-matcher
  [schema]
  (or (coerce/json-coercion-matcher schema)
      (workflow-node-coercion schema)
      (uuid-coercion schema)))

(defn replacer
  "Calls  replacement function on different types"
  [rfn x]
  (condp = (type x)
    clojure.lang.Keyword (-> x name rfn keyword)
    clojure.lang.MapEntry (update x 0 (partial replacer rfn))
    clojure.lang.PersistentArrayMap (map (partial replacer rfn) x)
    java.lang.String (rfn x)))

(defn underscore->hyphen
  "Converts underscores to hyphens"
  [x]
  (replacer #(clojure.string/replace % #"_" "-") x))

(defn hyphen->underscore
  "Converts hyphens to underscores"
  [x]
  (replacer #(clojure.string/replace % #"-" "_") x))
