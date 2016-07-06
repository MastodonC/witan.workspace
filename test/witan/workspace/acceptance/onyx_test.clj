(ns witan.workspace.acceptance.onyx-test
  (:require [clojure.test :refer :all]
            [schema.test :as st]
            [aero.core :refer [read-config]]
            [clojure.core.async :refer [pipe <!! put! close!]]
            [clojure.core.async.lab :refer [spool]]
            [witan.workspace.onyx :as o]
            [witan.workspace.function-catalog :as fc]
            [onyx
             [job :refer [add-task]]
             [test-helper :refer [with-test-env]]]
            [onyx.plugin
             [redis]
             [core-async :refer [get-core-async-channels]]]
            [onyx.tasks
             [core-async :as core-async]
             [redis :as redis]]
            [taoensso.carmine :as car :refer [wcar]]
            [witan.workspace.acceptance.config :refer [config]]
            [witan.workspace-api.onyx :refer [default-fn-wrapper
                                              default-pred-wrapper
                                              kw->fn]]))

(defn redis-conn []
  {:spec {:uri (get-in config [:redis-config :redis/uri])}})

(defn add-source-and-sink
  [job]
  (-> job
      (add-task (core-async/input :in (:batch-settings config)))
      (add-task (core-async/output :out (:batch-settings config)))))

(defn run-job
  ([job data]
   (run-job job data 7))
  ([job data n]
   (let [{:keys [env-config
                 peer-config]} config
         redis-spec (redis-conn)
         {:keys [out in]} (get-core-async-channels job)]
     (with-test-env [test-env [n env-config peer-config]]
       (pipe (spool [data :done]) in)
       (onyx.test-helper/validate-enough-peers! test-env job)
       (let [job-id (:job-id (onyx.api/submit-job peer-config job))
             result (<!! out)]
         (onyx.api/await-job-completion peer-config job-id)
         result)))))

(deftest linear-workspace-executed-on-onyx
  (testing "Linear workspace with no params"
    (let [state {:test "blah" :number 0}]
      (is (= (fc/my-inc state)
             (run-job
              (add-source-and-sink
               (o/workspace->onyx-job
                {:workflow [[:in :inc]
                            [:inc :out]]
                 :catalog [{:witan/name :inc
                            :witan/fn :witan.workspace.function-catalog/my-inc}]}
                config))
              state)))))
  (testing "Linear workspace with params"
    (let [state {:test "blah" :number 1}
          params {:x 3}
          onyx-job (add-source-and-sink
                    (o/workspace->onyx-job
                     {:workflow [[:in :mulX]
                                 [:mulX :out]]
                      :catalog [{:witan/name :mulX
                                 :witan/fn :witan.workspace.function-catalog/mulX
                                 :witan/params params}]}
                     config))]
      (is (= (fc/mulX state params)
             {:test "blah" :number 3}
             (run-job onyx-job state))))))

(deftest looping-workspace-executed-on-onyx
  (testing "loop to same"
    (let [state {:test "blah" :number 0}
          onyx-job (add-source-and-sink
                    (o/workspace->onyx-job
                     {:workflow [[:in :inc]
                                 [:inc [:enough? :out :inc]]]
                      :catalog [{:witan/name :inc
                                 :witan/fn :witan.workspace.function-catalog/my-inc}
                                {:witan/name :enough?
                                 :witan/fn :witan.workspace.function-catalog/gte-ten}]}
                     config))]
      (is (= (nth (iterate fc/my-inc state) 10)
             (run-job
              onyx-job
              state)))))
  (testing "loop to diff"
    (let [state {:test "blah" :number 0}
          onyx-job (add-source-and-sink
                    (o/workspace->onyx-job
                     {:workflow [[:in :inc]
                                 [:inc :mul2]
                                 [:mul2 [:loop? :out :inc]]]
                      :catalog [{:witan/name :inc
                                 :witan/fn :witan.workspace.function-catalog/my-inc}
                                {:witan/name :mul2
                                 :witan/fn :witan.workspace.function-catalog/mul2}
                                {:witan/name :loop?
                                 :witan/fn :witan.workspace.function-catalog/gte-ten}]}
                     config))]
      (is (= (first (drop-while (comp not fc/gte-ten)
                                (iterate (comp fc/mul2 fc/my-inc) state)))
             (run-job
              onyx-job
              state))))))

(deftest looping-workspace-executed-on-onyx-WITHOUT-pred-in-catalog
  (is (thrown-with-msg? Exception #"task->fn could not find task '\:loop\?' in the catalog."
                        (add-source-and-sink
                         (o/workspace->onyx-job
                          {:workflow [[:in :inc]
                                      [:inc [:loop? :out :inc]]]
                           :catalog [{:witan/name :inc
                                      :witan/fn :witan.workspace.function-catalog/my-inc}]}
                          config)))))

;; Execute before task
(defn log-before-batch
  [event lifecycle]
  (println "Executing before batch" (:lifecycle/task lifecycle))
  {})

(def log-calls
  {:lifecycle/before-batch log-before-batch})

(deftest loop+merge-workspace-executed-on-onyx
  (let [state {:test "blah" :number 2}
        onyx-job (add-source-and-sink
                  (o/workspace->onyx-job
                   {:workflow [[:in   :inc]
                               [:in   :mul2]
                               [:inc  :dupe]
                               [:dupe [:enough? :mulX :inc]]
                               [:mul2 :dupe2]
                               [:dupe2 :merge]
                               [:mulX :merge]
                               [:merge :out]]
                    :catalog [{:witan/name :inc
                               :witan/fn :witan.workspace.function-catalog/my-inc}
                              {:witan/name :mul2
                               :witan/fn :witan.workspace.function-catalog/mul2}
                              {:witan/name :dupe
                               :witan/fn :witan.workspace.function-catalog/dupe
                               :witan/params {:from :number
                                              :to :foo}}
                              {:witan/name :dupe2
                               :witan/fn :witan.workspace.function-catalog/dupe
                               :witan/params {:from :number
                                              :to :foo2}}
                              {:witan/name :mulX
                               :witan/fn :witan.workspace.function-catalog/mulX
                               :witan/params {:x 4}}
                              {:witan/name :merge
                               :witan/fn :clojure.core/identity}
                              {:witan/name :enough?
                               :witan/fn :witan.workspace.function-catalog/gte-ten}]}
                   config))
        onyx-job' onyx-job
        #_(update onyx-job :lifecycles concat [{:lifecycle/task :inc
                                                :lifecycle/calls :witan.workspace.acceptance.onyx-test/log-calls}
                                               {:lifecycle/task :dupe
                                                :lifecycle/calls :witan.workspace.acceptance.onyx-test/log-calls}
                                               {:lifecycle/task :mulX
                                                :lifecycle/calls :witan.workspace.acceptance.onyx-test/log-calls}])
        result (run-job onyx-job' state 13)
        prediction {:test "blah", :number 40, :foo2 4, :foo 10}]
    (is (= result prediction))))

(deftest merge-workspace-executed-on-onyx
  (let [state {:test "blah" :number 1}
        result (fc/add* (merge (fc/mul2* state) (fc/inc* state)))
        onyx-job (add-source-and-sink
                  (o/workspace->onyx-job
                   {:workflow [[:in :inc]
                               [:in :mult]
                               [:inc :sum]
                               [:mult :sum]
                               [:sum :out]]
                    :catalog [{:witan/name :inc
                               :witan/fn :witan.workspace.function-catalog/inc*}
                              {:witan/name :mult
                               :witan/fn :witan.workspace.function-catalog/mul2*}
                              {:witan/name :sum
                               :witan/fn :witan.workspace.function-catalog/add*}]
                    :task-scheduler :onyx.task-scheduler/balanced}
                   config))]
    (is (= result
           (run-job onyx-job state)))))


(comment [:get-births-data-year :at-risk-this-birth-yearu
          :get-births-data-year :at-risk-last-birth-year
          :at-risk-this-birth-year :births-pool
          :at-risk-last-birth-year :births-pool
          ;; :births-pool :births
          ;; :births :fert-rate-without-45-49
          ;; :fert-rate-without-45-49 :fert-rate-with-45-49
          ;; :at-risk-this-fert-last-year :estimated-sya-births-pool
          ;; :at-risk-last-fert-last-year :estimated-sya-births-pool
          ;; :fert-rate-with-45-49 :estimated-sya-births
          ;; :estimated-sya-births-pool :estimated-sya-births
          ;; :estimated-sya-births :estimated-births
          ;; :estimated-sya-births :historic-fertility
          ;; :estimated-births :scaling-factors
          ;; :actual-births :scaling-factors
          ;; :scaling-factors :historic-fertility
          ])
