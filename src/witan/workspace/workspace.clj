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
            [witan.workspace.util          :as util]
            [witan.workspace.event         :as ev]
            [clojure.core.async            :as async]
            [amazonica.aws.s3              :as s3]
            [witan.workspace.data          :as data]
            [clojure.data.csv              :as data-csv]
            [clojure.java.io               :as io]
            [clojure.core.matrix.dataset   :as ds]
            [clojure.core.matrix           :as m]
            [base64-clj.core               :as base64])
  (:import java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Downloading

(def s3-data-bucket "witan-data")
(def s3-result-bucket "witan-workspace-results")

(defn gunzip-text
  "https://gist.github.com/bpsm/1858654"
  [input]
  (with-open [input' (-> input io/input-stream GZIPInputStream.)]
    (slurp input')))

(defn gzip
  [input output & opts]
  (with-open [output (-> output io/output-stream GZIPOutputStream.)]
    (apply io/copy input output opts)))

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
  (assoc-in input [:witan/params :fn] (partial download-from-s3 bucket)))

(defn dataset->rows
  [d]
  (concat [(map name (get d :column-names))]
          (m/array d)
          #_(m/transpose (get d :columns))))

(defn data->csv
  [results]
  (into {}
        (map (fn [[key dataset]]
               (with-open [sw   (java.io.StringWriter.)
                           baos (java.io.ByteArrayOutputStream.)
                           gzos (java.util.zip.GZIPOutputStream. baos)]
                 (data-csv/write-csv sw (dataset->rows dataset))
                 (.write gzos (.getBytes (str sw)))
                 (.finish gzos)
                 (hash-map key (.toByteArray baos)))) results)))

(defn upload-to-s3
  [bucket path contents workspace-hash]
  (try
    (with-open [in (java.io.ByteArrayInputStream. contents)]
      (s3/put-object :bucket-name bucket
                     :key path
                     :input-stream in
                     :metadata {:content-type "text/csv"
                                :content-encoding "gzip"
                                ;;:user-metadata {:workspace workspace-hash}
                                }))
    (log/trace "File uploaded to S3:" bucket path)
    (catch Throwable e
      (log/error "Uploading data failed:" bucket path e)
      (throw e))))

(defn b64-workspace
  [workflow catalog]
  (base64/encode (str (pr-str workflow)
                      (pr-str catalog))))

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

(defmethod p/command-processor
  [:workspace/create-result-url "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] {:workspace/result-location s/Str})
    (process [_ {:keys [workspace/result-location]} _]
      (log/warn "GENERATING PRESIGNED URL - PLEASE DEPRECATE THIS COMMAND IN FAVOR OF DATASTORE")
      (let [ttl (-> 1 t/hours t/from-now)
            url (s3/generate-presigned-url
                 s3-result-bucket
                 result-location
                 (-> 1 t/hours t/from-now))]
        {:event/key :workspace/result-url-created
         :event/version "1.0.0"
         :event/params {:workspace/result-url (str url)
                        :workspace/original-location result-location
                        :workspace/ttl (util/timestamp :basic-date-time ttl)}}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events

(defn send-error-event!
  [event-sender receipt error]
  (ev/send-event! event-sender (ev/create-event
                                receipt
                                {:event/key :workspace/finished-with-errors
                                 :event/version "1.0.0"
                                 :event/params {:error error}})))

(defmethod p/event-processor
  [:workspace/saved "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] FullWorkspaceSchema)
    (process [_ wsp _]
      (log/debug "TODO Saving workspace..." wsp))))

(defmethod p/event-processor
  [:workspace/started-running "1.0.0"]
  [c v]
  (reify p/Processor
    (params [_] FullWorkspaceSchema)
    (process [_ {:keys [workspace/name
                        workspace/id
                        workspace/catalog
                        workspace/workflow
                        command/receipt] :as wsp} {:keys [db event-sender]}]
      ;; TODO Bruce - dagobah!
      ;; Reconcile that we might have run this workspace before (workflow + catalog)
      ;; so check and link to generated result.
      (log/debug "Running workspace" name id (keys wsp))
      (async/go
        (try
          (let [fixed-catalog (mapv #(if (= (:witan/type %) :input) (fix-input s3-data-bucket %) %) catalog)
                wn (s/with-fn-validation
                     (wex/build! {:workflow  workflow
                                  :catalog   fixed-catalog
                                  :contracts q/all-functions}))
                result (into {} (wex/run!! wn {}))]
            (log/trace "Workspace" id "run complete.")
            (if (contains? result :error)
              (let [{:keys [error]} result]
                (log/error "An error occurred when running the workspace:" error)
                (send-error-event! event-sender receipt error))
              (let [result-csv (data->csv result)]
                (log/trace "Workspace" id "produced the following results:" (keys result-csv))
                (let [paths (into {}
                                  (map (fn [[result-id csv]]
                                         (let [path (str id "/"
                                                         (util/timestamp) "/"
                                                         (-> (str result-id)
                                                             (subs 1)
                                                             (clojure.string/replace "/" "__")) ".csv")]
                                           (upload-to-s3 s3-result-bucket path csv
                                                         #_(b64-workspace workflow catalog)
                                                         nil)
                                           (hash-map result-id path))) result-csv))]
                  (ev/send-event! event-sender (ev/create-event
                                                receipt
                                                {:event/key :workspace/finished-with-results
                                                 :event/version "1.0.0"
                                                 :event/params {:workspace/results paths}}))))))
          (catch Throwable e
            (log/error "An error occurred when running the workspace:" e)
            (send-error-event! event-sender receipt (str e))))))))
