(ns witan.workspace.onyx-test
  (:require [clojure.test :refer :all]
            [schema.test :as st]
            [witan.workspace.onyx :as o]
            [witan.workspace.function-catalog :as fc]))

(use-fixtures :once st/validate-schemas)

(defn workspace
  [{:keys [workflow contracts catalog] :as raw}]
  (assoc raw
         :workflow (or workflow [])
         :contracts (or contracts [])
         :catalog (or catalog [])))

(def config
  {:redis-config {:redis/uri "redis"}
   :batch-settings {:onyx/batch-size 1}})

(defn batch-size
  [c]
  (get-in c [:batch-settings :onyx/batch-size]))

(defn redis-uri
  [c]
  (get-in c [:redis-config :redis/uri]))

(defn all-same
  [coll]
  (= 1 (count (set coll))))

(deftest witan-workflow->onyx-workflow
  (testing "Simple liner workflows should be unchanged"
    (is (= {:workflow [[:in :inc]
                       [:inc :out]]
            :flow-conditions []
            :lifecycles []}
           (o/witan-workflow->onyx-workflow
            {:workflow [[:in :inc]
                        [:inc :out]]}
            {}))))

  (testing "Loops should be introduced when branches are used"
    (let [onyx-job (o/workspace->onyx-job
                    (workspace
                     {:workflow [[:in :inc]
                                 [:inc [:enough? :out :inc]]]})
                    config)
          onyx-job-pred-wraps (o/workspace->onyx-job
                               (workspace
                                {:workflow [[:in :inc]
                                            [:inc [:enough? :out :inc]]]
                                 :catalog [{:witan/name :enough?
                                            :witan/fn :witan.workspace.function-catalog/enough?
                                            :witan/version "1.0"
                                            :witan/inputs [{:witan/input-src-key :foo}]}]})
                               (assoc config
                                      :pred-wrapper :witan.workspace.function-catalog/test-pred-wrapper))]
      (is (= [[:in :write-state-inc]
              [:inc :write-state-inc]
              [:read-state-inc :inc]
              [:inc :out]]
             (:workflow onyx-job)))
      (is (= [{:flow/from :inc,
               :flow/to [:write-state-inc],
               :flow/predicate [:not :enough?]}
              {:flow/from :inc,
               :flow/to [:out],
               :flow/predicate :enough?}]
             (:flow-conditions onyx-job)))
      (is (= [{:flow/from :inc,
               :flow/to [:write-state-inc],
               :flow/predicate [:not [:witan.workspace.function-catalog/test-pred-wrapper :witan/fn]]
               :witan/fn :witan.workspace.function-catalog/enough?}
              {:flow/from :inc,
               :flow/to [:out],
               :flow/predicate [:witan.workspace.function-catalog/test-pred-wrapper :witan/fn]
               :witan/fn :witan.workspace.function-catalog/enough?}]
             (:flow-conditions onyx-job-pred-wraps)))
      (is (= [{:onyx/name :write-state-inc,
               :onyx/plugin :onyx.plugin.redis/writer,
               :onyx/type :output,
               :onyx/medium :redis,
               :redis/uri (redis-uri config)
               :redis/cmd :redis/set
               :onyx/batch-size (batch-size config)}
              {:onyx/name :read-state-inc,
               :onyx/plugin :onyx.plugin.redis/reader,
               :onyx/type :input,
               :onyx/medium :redis,
               :onyx/max-peers 1
               :redis/uri (redis-uri config)
               :redis/cmd :redis/get
               :onyx/batch-size (batch-size config)}]
             (mapv #(dissoc % :redis/key) (:catalog onyx-job))))
      (let [redis-key-entries (map :redis/key (:catalog onyx-job))]
        (is (every?
             #(.startsWith (name %) "state-inc-")
             redis-key-entries))
        (is (all-same redis-key-entries)))))

  (testing "merge definitions result in a gathering step"
    (let [onyx-job (o/witan-workflow->onyx-workflow
                    {:workflow [[:in :inc]
                                [:in :mult]
                                [:inc :sum]
                                [:mult :sum]
                                [:sum :out]]
                     :catalog []
                     :task-scheduler :onyx.task-scheduler/balanced}
                    config)]
      (is (= [[:in :inc]
              [:in :mult]
              [:inc :write-merge-inc-mult-for-sum]
              [:mult :write-merge-inc-mult-for-sum]
              [:sum :out]
              [:read-merge-inc-mult-for-sum :sum]]
             (:workflow onyx-job)))
      (is (= [{:onyx/name :write-merge-inc-mult-for-sum
               :onyx/plugin :onyx.plugin.redis/writer
               :onyx/type :output
               :onyx/medium :redis
               :redis/cmd :redis/rpush
               :redis/uri (get-in config [:redis-config :redis/uri])
               :onyx/batch-size 1}
              {:onyx/plugin :onyx.plugin.redis/reader
               :onyx/medium :redis
               :onyx/type :input
               :onyx/name :read-merge-inc-mult-for-sum
               :onyx/max-peers 1
               :redis/length 2
               :redis/uri (get-in config [:redis-config :redis/uri])
               :redis/cmd :redis/lrange
               :onyx/batch-size 1}]
             (mapv #(dissoc % :redis/key) (:catalog onyx-job))))))

  (testing "merge within a loop"
    (let [onyx-job (o/witan-workflow->onyx-workflow
                    {:workflow [[:in :x]
                                [:in :y]
                                [:x  :z]
                                [:y  :foo]
                                [:z  :foo]
                                [:foo [:loop? :x :a]]]
                     :catalog []
                     :task-scheduler :onyx.task-scheduler/balanced}
                    config)]
      (is (= [[:in :x]
              [:in :y]
              [:x :z]
              [:y :write-merge-y-z-for-foo]
              [:z :write-merge-y-z-for-foo]
              [:foo :write-state-foo]
              [:read-merge-y-z-for-foo :foo]
              [:read-state-foo :a]
              [:foo :x]]
             (:workflow onyx-job)))))
  (testing "branch, loop, merge"
    (let [onyx-job (o/witan-workflow->onyx-workflow
                    {:workflow [[:in :a]
                                [:a  :b]
                                [:a  :c]
                                [:c  :d]
                                [:d [:loop? :c :f]]
                                [:f  :g]
                                [:b  :g]]
                     :catalog []
                     :task-scheduler :onyx.task-scheduler/balanced}
                    config)]
      (= [[:in :a]
          [:a :b]
          [:a :c]
          [:c :d]
          [:d :write-state-d]
          [:f :write-merge-f-b-for-g]
          [:b :write-merge-f-b-for-g]
          [:read-merge-f-b-for-g :g]
          [:read-state-d :f]
          [:d :c]]
         (:workflow onyx-job))))
  (testing "just loop then merge"
    (let [onyx-job (o/witan-workflow->onyx-workflow
                    {:workflow [[:in1 :a]
                                [:in2 :c]
                                [:a   :b]
                                [:b   [:loop? :a :d]]
                                [:d   :e]
                                [:c   :e]]
                     :catalog []
                     :task-scheduler :onyx.task-scheduler/balanced}
                    config)]
      (is (= [[:in1 :a]
              [:in2 :c]
              [:a :b]
              [:b :write-state-b]
              [:d :write-merge-d-c-for-e]
              [:c :write-merge-d-c-for-e]
              [:read-merge-d-c-for-e :e]
              [:read-state-b :d]
              [:b :a]]
             (:workflow onyx-job)))))

  (testing "merge then loop"
    (let [onyx-job (o/witan-workflow->onyx-workflow
                    {:workflow [[:in1 :a]
                                [:in2 :c]
                                [:a   :b]
                                [:c   :b]
                                [:b   :d]
                                [:d   [:loop? :b :e]]
                                [:e   :f]]
                     :catalog []
                     :task-scheduler :onyx.task-scheduler/balanced}
                    config)]
      (is (= [[:in1 :a]
              [:in2 :c]
              [:a :write-merge-a-c-for-b]
              [:c :write-merge-a-c-for-b]
              [:b :d]
              [:d :write-state-d]
              [:e :f]
              [:read-merge-a-c-for-b :b]
              [:read-state-d :e]
              [:d :b]]
             (:workflow onyx-job))))))

(deftest witan-catalog->onyx-catalog
  (testing "Simple function gets relabeled"
    (is (= {:catalog
            [{:onyx/name :inc
              :onyx/fn   :witan.workspace.function-catalog/my-inc
              :onyx/type :function
              :onyx/batch-size (batch-size config)}]}
           (o/witan-catalog->onyx-catalog
            {:catalog
             (filter #(= :inc (:witan/name %))
                     fc/catalog)}
            config))))
  (testing "Function with params gets relabeled"
    (is (= {:catalog
            [{:onyx/name :mulx
              :onyx/fn   :witan.workspace.function-catalog/mulX
              :onyx/type :function
              :onyx/batch-size (batch-size config)
              :witan/params {:x 3}
              :onyx/params [:witan/params]}]}
           (o/witan-catalog->onyx-catalog
            {:catalog
             (filter #(= :mulx (:witan/name %))
                     fc/catalog)}
            config))))
  (testing "Function wrapper is applied"
    (is (= {:catalog
            [{:onyx/name :mulx
              :onyx/fn   :witan.workspace.function-catalog/test-wrapper
              :onyx/type :function
              :onyx/batch-size (batch-size config)
              :onyx/params [:witan/fn :witan/params]
              :witan/params {:x 3}
              :witan/fn :witan.workspace.function-catalog/mulX}]}
           (o/witan-catalog->onyx-catalog
            {:catalog
             (filter #(= :mulx (:witan/name %))
                     fc/catalog)}
            (assoc config
                   :fn-wrapper :witan.workspace.function-catalog/test-wrapper))))))


(deftest witan-workspace->onyx-job
  (is (= {:workflow [[:in :inc]
                     [:inc :out]]
          ;;:contracts []
          :catalog [{:onyx/name :inc
                     :onyx/fn   :witan.workspace.function-catalog/my-inc
                     :onyx/type :function
                     :onyx/batch-size (batch-size config)}]
          :flow-conditions []
          :lifecycles []
          :task-scheduler :onyx.task-scheduler/balanced}
         (o/workspace->onyx-job
          (workspace
           {:workflow [[:in :inc]
                       [:inc :out]]
            :catalog [{:witan/name :inc
                       :witan/fn :witan.workspace.function-catalog/my-inc
                       :witan/version "1.0"
                       :witan/inputs [{:witan/input-src-fn   'witan.workspace.core-test/get-data
                                       :witan/input-src-key  1
                                       :witan/input-dest-key :number}]}]})
          config))))
