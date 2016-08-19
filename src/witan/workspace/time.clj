(ns witan.workspace.time
  (:require [clj-time.core              :as t]
            [clj-time.format            :as tf]))

(defn timestamp
  ([fmt time]
   (tf/unparse (tf/formatters fmt) time))
  ([fmt]
   (tf/unparse (tf/formatters fmt) (t/now)))
  ([]
   (timestamp :basic-date-time (t/now))))

(defn hours-from-now
  [n]
  (-> n t/hours t/from-now))
