(ns witan.workspace.schema
  (:require [schema.core :as s]
            [schema-contrib.core :as sc]
            [witan.workspace.util :as util]))

(def KafkaCommandBase
  {:received-at sc/ISO-Date-Time
   :handled-by s/Str
   :origin (s/pred util/ip?)})

(def KafkaEvent
  {:event s/Keyword
   :params s/Any
   :version (s/pred util/version?)
   :command {:version (s/pred util/version?)
             :original s/Keyword
             :id s/Str}
   :id s/Uuid
   :origin s/Str
   :created-at sc/ISO-Date-Time})
