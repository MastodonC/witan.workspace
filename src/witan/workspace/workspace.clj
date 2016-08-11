(ns witan.workspace.workspace
  (:require [taoensso.timbre               :as log]
            [witan.workspace.protocols     :as p]
            [schema.core                   :as s]
            [clojure.stacktrace            :as st]
            [witan.gateway.schema          :as wgs]
            [witan.workspace-api.schema    :as was]
            [witan.workspace-api.utils     :as wau]
            [witan.workspace-executor.core :as wex]
            [witan.workspace.query         :as q]
            [clojure.core.async            :as async]
            [amazonica.aws.s3              :as s3]
            [witan.workspace.data          :as data]
            [clojure.data.csv              :as data-csv]
            [clojure.java.io               :as io])
  (:import java.util.zip.GZIPInputStream))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Downloading

(def s3-bucket "witan-data")

(defn gunzip-text
  "https://gist.github.com/bpsm/1858654"
  [input]
  (with-open [input' (-> input io/input-stream GZIPInputStream.)]
    (slurp input')))

(defn download-from-s3
  [bucket src schema]
  (let [file        (s3/get-object bucket src)
        read-fn     (if (= (get-in file [:object-metadata :content-type])
                           "application/x-gzip") gunzip-text slurp)
        parsed-csv  (data-csv/read-csv (read-fn (:input-stream file)))
        parsed-data (vec (rest parsed-csv))
        headers     (map clojure.string/lower-case (first parsed-csv))]
    (data/->dataset headers parsed-data schema)))

(defn fix-input
  [bucket input]
  (assoc-in input [:witan/params :fn] (partial download-from-s3 s3-bucket)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schema

(def FullWorkspaceSchema
  (-> (get wgs/WorkspaceMessage "1.0.0")
      (dissoc #schema.core.OptionalKey{:k :workspace/workflow}
              #schema.core.OptionalKey{:k :workspace/catalog})
      (assoc :workspace/workflow (:workflow was/Workspace)
             :workspace/catalog  (:catalog was/Workspace))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands

(defmethod p/command-processor
  [:workspace/save "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] {:workspace/to-save (get wgs/WorkspaceMessage "1.0.0")})
    (process [_ {:keys [workspace/to-save]} _]
      (log/debug "SAVING WORKSPACE" to-save)
      {:event/key :workspace/saved
       :event/version "1.0.0"
       :event/params to-save})))

(defmethod p/command-processor
  [:workspace/run "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] {:workspace/to-run FullWorkspaceSchema})
    (process [_ {:keys [workspace/to-run]} _]
      (log/debug "RUNNING WORKSPACE" to-run)
      {:event/key :workspace/started-running
       :event/version "1.0.0"
       :event/params to-run})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events

(defmethod p/event-processor
  [:workspace/saved "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] FullWorkspaceSchema)
    (process [_ wsp {:keys [db]}]
      (log/debug "Saving workspace..." wsp))))

(defmethod p/event-processor
  [:workspace/started-running "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] FullWorkspaceSchema)
    (process [_ {:keys [workspace/name
                        workspace/id
                        workspace/catalog
                        workspace/workflow] :as wsp} {:keys [db]}]
      (log/debug "Running workspace" name id)
      (async/go
        (try
          (let [fixed-catalog (mapv #(if (= (:witan/type %) :input) (fix-input s3-bucket %) %) catalog)
                wn (s/with-fn-validation
                     (wex/build! {:workflow  workflow
                                  :catalog   fixed-catalog
                                  :contracts q/all-functions}))
                result (wex/run!! wn {})]
            (log/debug result))
          (catch Throwable e
            (log/error "An error occurred when running the workspace:" e)))))))
