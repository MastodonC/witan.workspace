(ns witan.workspace.util
  (:require [cheshire.core :as json]))

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
       (json/generate-string )
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
    java.lang.String (-> x rfn)))

(defn underscore->hyphen
  "Converts underscores to hyphens"
  [x]
  (replacer #(clojure.string/replace % #"_" "-") x))

(defn hyphen->underscore
  "Convers hyphens to underscores"
  [x]
  (replacer #(clojure.string/replace % #"-" "_") x))
