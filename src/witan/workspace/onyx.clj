(ns witan.workspace.onyx
  (:require [com.rpl.specter :as spec
             :refer [transform select selected? filterer setval view collect comp-paths keypath
                     END ALL LAST FIRST VAL BEGINNING STOP]]
            [com.rpl.specter.macros :refer [defpath]]
            [onyx.job :as onyx.job]
            [onyx.plugin
             [redis :as redis]
             [core-async :refer [get-core-async-channels]]]
            [onyx.tasks
             [core-async :as core-async]
             [redis :as redis-tasks]]
            [schema.core :as s]
            [witan.workspace.schema :as as]))

(defn branch?
  [v]
  (vector? (second v)))
3
(defn third
  [v]
  (nth v 2))

(def llast (comp last last))
(def ssecond (comp second second))
(def flast (comp first last))
(def lfirst (comp last first))

(defn equals
  [v]
  (fn [c]
    (= v c)))

(defpath value-path [key]
  (select*
   [this structure next-fn]
   (next-fn (filter (equals key) structure)))
  (transform*
   [this structure next-fn]
   (if (= structure key)
     (next-fn key)
     structure)))

(def wf-to-task
  (comp-paths :workflow ALL LAST value-path))

(def wf-end
  (comp-paths :workflow END))

(def wf-node
  (comp-paths :workflow ALL value-path))

(def fc-end
  (comp-paths :flow-conditions END))

(def lc-end
  (comp-paths :lifecycles END))

(defn add-task
  [task job]
  (onyx.job/add-task job task))

(defn write-state-kw
  [branch]
  (keyword (str "write-state-" (name (first branch)))))

(defn read-state-kw
  [branch]
  (keyword (str "read-state-" (name (first branch)))))

(defn redis-key-for
  [branch]
  (keyword (str "state-" (name (first branch)) "-" (str (java.util.UUID/randomUUID)))))

(defn flow-condition
  [node pred params]
  [(merge {:flow/from (first node)
           :flow/to [(second node)]
           :flow/predicate pred}
          params)])

(defn task->fn
  [cat task]
  (if-let [fn (some #(when (= task (:onyx/name %)) (or (:witan/fn %) (:onyx/fn %))) cat)]
    fn
    (throw (Exception. (str "task->fn could not find task '" task "' in the catalog.")))))

(defn branch-expanders
  [config]
  (let [redis-uri (:redis/uri (:redis-config config))
        batch-settings (:batch-settings config)
        pred-wrapper (:pred-wrapper config)]
    (fn
      [branch]
      (let [write-state (write-state-kw branch)
            read-state (read-state-kw branch)
            redis-key (redis-key-for branch)
            loop-node [(first branch) write-state]
            exit-node [(first branch) (ssecond branch)]
            pred-params #(when pred-wrapper {:witan/fn (task->fn % (flast branch))})
            branch-fn (if pred-wrapper
                        [pred-wrapper :witan/fn]
                        (flast branch))]
        (fn [raw]
          (->> raw
               (transform
                (wf-to-task (llast branch))
                (constantly write-state))
               (setval
                wf-end
                [[read-state (llast branch)]])
               (transform
                (wf-node branch)
                (constantly
                 loop-node))
               (setval
                wf-end
                [exit-node])
               (setval
                fc-end
                (flow-condition
                 loop-node
                 [:not branch-fn]
                 (pred-params (:catalog raw))))
               (setval
                fc-end
                (flow-condition
                 exit-node
                 branch-fn
                 (pred-params (:catalog raw))))
               (add-task (redis-tasks/writer write-state redis-uri :redis/set redis-key batch-settings))
               (add-task (redis-tasks/reader read-state redis-uri redis-key batch-settings))))))))

(defn merge-expanders
  [config]
  (let [redis-uri (:redis/uri (:redis-config config))
        batch-settings (:batch-settings config)]
    (fn [merging-nodes]
      (let [target (lfirst merging-nodes)
            write-merge-task (keyword (str "write-merge-"
                                           (clojure.string/join "-" (map (comp name first) merging-nodes))
                                           "-for-"
                                           (name target)))
            read-merge-task (keyword (str "read-merge-"
                                          (clojure.string/join "-" (map (comp name first) merging-nodes))
                                          "-for-"
                                          (name target)))
            redis-key (redis-key-for [write-merge-task])]
        (fn [raw]
          (->>
           (reduce #(transform
                     (wf-node %2)
                     (fn [n]
                       [(first n) write-merge-task])
                     %1)
                   raw merging-nodes)
           (setval
            wf-end
            [[read-merge-task target]])
           (add-task (redis-tasks/writer write-merge-task redis-uri :redis/rpush redis-key batch-settings))
           (add-task (redis-tasks/read-list read-merge-task redis-uri redis-key (count merging-nodes) batch-settings))))))))

(defn schema-wrapper
  [input-schema output-schema fn-wrapped segment]
  (s/validate input-schema segment)
  (let [output (fn-wrapped segment)]
    (s/validate output-schema output)
    output))

(def onyx-defaults
  {:task-scheduler :onyx.task-scheduler/balanced})

(defn branch-expander
  [workspace config]
  ((->>
    (select [:workflow ALL branch?] workspace)
    (map (branch-expanders config))
    (apply comp identity))
   workspace))

(defn merge-expander
  [workspace config]
  ((->>
    (:workflow workspace)
    (remove branch?)
    (group-by second)
    vals
    (filter #(< 1 (count %)))
    (map (merge-expanders config))
    (apply comp identity))
   workspace))

(defn identify-branches
  "Pick out branches and create a map which associates them with keys"
  [workflow]
  (into {} (map-indexed (fn [i [a pred]]
                          (hash-map pred (->> i
                                              (str "_pred_refit_")
                                              (keyword))))
                        (select [ALL branch?] workflow))))

(defn switch-out-branches
  "Using a branch map, replace instances of branch with the associated keyword"
  [preds]
  (fn [workflow]
    (reduce (fn [a [from to]]
              (if-let [new (get preds to)]
                (conj a [from new])
                (conj a [from to]))) [] workflow)))

(defn switch-in-branches
  "Using a branch map, replace instances of keyword with the associated branch"
  [preds]
  (fn [workflow]
    (reduce (fn [a [from to]]
              (if-let [special (re-find #"^_pred_refit_\d+" (name to))]
                (let [branch (some (fn [kv]
                                     (when (= (second kv)
                                              (keyword special)) (first kv))) preds)]
                  (conj a [from branch]))
                (conj a [from to]))) [] workflow)))

(defn witan-workflow->onyx-workflow
  [{:keys [workflow] :as workspace} config]
  (let [preds (identify-branches workflow)]
    (-> workspace
        (assoc :flow-conditions []
               :lifecycles [])
        (update :workflow (switch-out-branches preds))
        (merge-expander config)
        (update :workflow (switch-in-branches preds))
        (branch-expander config))))

(def onyx-catalog-entry-template
  {:onyx/name :witan/name
   :onyx/fn (fn [cat config]
              (if (contains? config :fn-wrapper)
                (get config :fn-wrapper)
                (get cat :witan/fn)))
   :onyx/type (constantly :function)
   :onyx/n-peers (fn [_ config]
                   (get-in config [:task-settings :onyx/n-peers]))
   :onyx/batch-size (fn [_ config]
                      (get-in config [:batch-settings :onyx/batch-size]))
   :onyx/batch-timeout (fn [_ config]
                         (get-in config [:batch-settings :onyx/batch-timeout]))
   :onyx/params (fn [cat config]
                  (->> (conj []
                             (when (contains? config :fn-wrapper) :witan/fn)
                             (when (contains? cat :witan/params) :witan/params))
                       (keep identity)
                       (vec)
                       (not-empty)))
   ;;;;;;;
   :witan/params (fn [cat _] (:witan/params cat))
   :witan/fn (fn [cat config]
               (when (contains? config :fn-wrapper)
                 (:witan/fn cat)))})

(defn witan-catalog->onyx-catalog
  [{:keys [catalog workflow] :as workspace} config]
  (let [branches (set (select [ALL branch? LAST FIRST] workflow))]
    (update workspace
            :catalog
            #(mapv (fn [cat]
                     (let [r (reduce-kv
                              (fn [acc onyx-key getter]
                                (if-let [r (getter cat config)]
                                  (assoc acc onyx-key r)
                                  acc))
                              {}
                              onyx-catalog-entry-template)]
                       (if (contains? branches (:witan/name cat))
                         (assoc r :witan/pred? true)
                         r)))
                   %))))

(s/defn workspace->onyx-job
  [{:keys [workflow] :as workspace} :- as/Workspace
   config]
  (->
   (merge workspace onyx-defaults)
   (dissoc :contracts)
   (witan-catalog->onyx-catalog config)
   (witan-workflow->onyx-workflow config)
   (update :catalog #(remove :witan/pred? %))))
