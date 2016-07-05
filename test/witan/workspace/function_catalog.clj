(ns witan.workspace.function-catalog
  (:require [schema.core :as s]
            [witan.workspace-api :refer [defworkflowfn
                                         defworkflowpred]]))

(defworkflowpred gte-ten
  "true if number is greater than 10"
  {:witan/name :witan.test-preds/gte-ten
   :witan/version "1.0"
   :witan/input-schema {:number s/Num}}
  [{:keys [number] :as msg} _]
  (<= 10 number))

(defworkflowfn my-inc
  "increments a number"
  {:witan/name :witan.test-funcs/inc
   :witan/version "1.0"
   :witan/input-schema {:number s/Num}
   :witan/output-schema {:number s/Num}}
  [{:keys [number] :as x} _]
  {:number (inc number)})

(defworkflowfn inc*
  "increments a number"
  {:witan/name :witan.test-funcs/inc*
   :witan/version "1.0"
   :witan/input-schema {:number s/Num}
   :witan/output-schema {:foo s/Num}}
  [{:keys [number]} _]
  {:foo (inc number)})

(defworkflowfn mul2
  "multiplies a number by 2"
  {:witan/name :witan.test-funcs/mul2
   :witan/version "1.0"
   :witan/input-schema {:number s/Num}
   :witan/output-schema {:number s/Num}}
  [{:keys [number]} _]
  {:number (* 2 number)})

(defworkflowfn mul2*
  "multiplies a number by 2"
  {:witan/name :witan.test-funcs/mul2*
   :witan/version "1.0"
   :witan/input-schema {:number s/Num}
   :witan/output-schema {:mult s/Num}}
  [{:keys [number]} _]
  {:mult (* 2 number)})

(defworkflowfn mulX
  "multiplies a number by X"
  {:witan/name :witan.test-funcs/mulX
   :witan/version "1.0"
   :witan/input-schema {:number s/Num}
   :witan/param-schema {:x s/Num}
   :witan/output-schema {:number s/Num}}
  [{:keys [number] :as a} {:keys [x] :as b}]
  {:number (* number x)})

(defworkflowfn add
  "Adds number and to-add"
  {:witan/name :witan.test-funcs/add
   :witan/version "1.0"
   :witan/input-schema {:number s/Num
                        :to-add s/Num}
   :witan/output-schema {:number s/Num}}
  [{:keys [number to-add]} _]
  {:number (+ number to-add)})

(defworkflowfn add*
  "Adds number and to-add"
  {:witan/name :witan.test-funcs/add
   :witan/version "1.0"
   :witan/input-schema {:foo s/Num
                        :mult s/Num}
   :witan/output-schema {:number s/Num}}
  [{:keys [foo mult]} _]
  {:number (+ foo mult)})

(defworkflowfn ->str
  "Converts thing to string"
  {:witan/name :witan.test-funcs/->str
   :witan/version "1.0"
   :witan/input-schema {:thing s/Any}
   :witan/output-schema {:out-str s/Any}}
  [{:keys [thing]} _]
  {:out-str (str thing)})

(defn source-data
  []
  [{:number 0}])

;; (defn gte-ten
;;   [_ _ {:keys [number] :as msg} _]
;;   (<= 10 number))

;; (defn my-inc
;;   [{:keys [number] :as msg}]
;;   (println "inc number" number)

;;   (update msg
;;           :number
;;           inc))

;; (defn mult
;;   [{:keys [number] :as msg}]
;;   (->
;;    msg
;;    (assoc :mult 2)
;;    (dissoc :number)))

;; (defn sum
;;   [{:keys [number mult] :as msg}]
;;   (->
;;    msg
;;    (assoc :number (* number mult))
;;    (dissoc :mult)))

;; (defn mul2
;;   [{:keys [number]}]
;;   {:number (* number 2)})

;; (defn mulX
;;   [{:keys [number]} {:keys [x]}]
;;   (println "mulX number" number "x" x)
;;   {:number (* number x)})

;; (defn add
;;   [{:keys [number to-add]}]
;;   {:number (+ number to-add)})

;; (defn ->str
;;   [{:keys [thing]}]
;;   {:out-str (str thing)})

;; (defn source-data
;;   []
;;   [{:number 0}])

;; (defn enough?
;;   [{:keys [number]}]
;;   (> number 10))


#_(def contracts
    [{:witan/fn      :foo/inc
      :witan/impl    'witan.workspace.function-catalog/inc*
      :witan/version "1.0"
      :witan/params-schema nil
      :witan/inputs  [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}]
      :witan/outputs [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}]}
     {:witan/fn      :foo/mul2
      :witan/impl    'witan.workspace.function-catalog/mul2
      :witan/version "1.0"
      :witan/params-schema nil
      :witan/inputs  [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}]
      :witan/outputs [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}]}
     {:witan/fn      :foo/mulX
      :witan/impl    'witan.workspace.function-catalog/mulX
      :witan/version "1.0"
      :witan/params-schema MulXParams
      :witan/inputs  [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}]
      :witan/outputs [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}]}
     {:witan/fn      :foo/add
      :witan/impl    'witan.workspace.function-catalog/add
      :witan/version "1.0"
      :witan/params-schema nil
      :witan/inputs  [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}
                      {:witan/schema       FooNumber
                       :witan/key          :to-add
                       :witan/display-name "To add"}]
      :witan/outputs [{:witan/schema       FooNumber
                       :witan/key          :number
                       :witan/display-name "Number"}]}

     {:witan/fn      :foo/->str
      :witan/impl    'witan.workspace.function-catalog/->str
      :witan/version "1.0"
      :witan/inputs  [{:witan/schema       s/Any
                       :witan/key          :thing
                       :witan/display-name "The value we want to string-ify"}]
      :witan/outputs [{:witan/schema       s/Str
                       :witan/key          :out-str
                       :witan/display-name "String representation"}]}])

(def catalog [{:witan/name :inc
               :witan/fn :witan.workspace.function-catalog/my-inc
               :witan/version "1.0"
               :witan/inputs [{:witan/input-src-fn   'witan.workspace.core-test/get-data
                               :witan/input-src-key  1
                               :witan/input-dest-key :number}]}
              {:witan/name :source-data
               :witan/fn :witan.workspace.function-catalog/source-data
               :witan/version "1.0"
               :witan/inputs [{:witan/input-src-fn   'witan.workspace.core-test/get-data
                               :witan/input-src-key  1
                               :witan/input-dest-key :number}]}
              {:witan/name :mul2
               :witan/fn :witan.workspace.function-catalog/mul2
               :witan/version "1.0"
               :witan/inputs [{:witan/input-src-key :number}]}
              {:witan/name :mulx
               :witan/fn :witan.workspace.function-catalog/mulX
               :witan/version "1.0"
               :witan/params {:x 3}
               :witan/inputs [{:witan/input-src-key :number}]}])
