(ns witan.workspace.util
  (:require [cheshire.core :as json]
            [schema.coerce :as coerce]
            [witan.workspace-api.schema :as was]))

(defn json->clojure
  [json]
  (json/parse-string json true))

;; http://stackoverflow.com/questions/82064/a-regex-for-version-number-parsing"
(defn version?
  [version-str]
  (first (re-find #"^(\d+\.)(\d+\.)?(\*|\d+)$" version-str)))

(defn ip?
  [ip-str]
  (re-find #"^\d{1,3}.\d{1,3}.\d{1,3}.\d{1,3}$" ip-str))

;; https://blog.juxt.pro/posts/condas.html
(defmacro condas->
  "A mixture of cond-> and as-> allowing more flexibility in the test and step forms"
  [expr name & clauses]
  (assert (even? (count clauses)))
  (let [pstep (fn [[test step]] `(if ~test ~step ~name))]
    `(let [~name ~expr
           ~@(interleave (repeat name) (map pstep (partition 2 clauses)))]
       ~name)))

(defn iso-date-time-now
  "The current date and time in iso-date-time format"
  []
  (->> (java.util.Date.)
       (json/generate-string)
       (drop 1)
       (butlast)
       (apply str)))

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
  "Convers hyphens to underscores"
  [x]
  (replacer #(clojure.string/replace % #"-" "_") x))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Custom coercion func

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
