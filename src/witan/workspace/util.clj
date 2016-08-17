(ns witan.workspace.util
  (:require [taoensso.timbre            :as log]
            [cheshire.core              :as json]
            [witan.workspace.protocols  :as p]))

;; https://blog.juxt.pro/posts/condas.html
(defmacro condas->
  "A mixture of cond-> and as-> allowing more flexibility in the test and step forms"
  [expr name & clauses]
  (assert (even? (count clauses)))
  (let [pstep (fn [[test step]] `(if ~test ~step ~name))]
    `(let [~name ~expr
           ~@(interleave (repeat name) (map pstep (partition 2 clauses)))]
       ~name)))
