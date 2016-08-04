(ns witan.workspace.handler
  (:require [compojure.core            :refer :all]
            [taoensso.timbre           :as log]
            [schema.core               :as s]
            [cognitect.transit         :as tr]
            [outpace.schema-transit    :as st]
            [clojure.data.codec.base64 :as b64]
            [witan.workspace.query     :as q])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn transitize
  [x]
  (let [out (ByteArrayOutputStream. 4096)]
    (tr/write
     (tr/writer out
                :json-verbose
                {:handlers st/cross-platform-write-handlers})
     x)
    (ByteArrayInputStream. (.toByteArray out))))

(defn handle-query
  [encoded-query components]
  (let [decoded-query (-> encoded-query
                          (.getBytes "utf-8")
                          (b64/decode)
                          (String.)
                          (read-string))
        _ (log/debug "Got query:" decoded-query)]
    (try
      {:status 200
       :body (transitize (q/query decoded-query (:db components)))}
      (catch Exception e {:status 500
                          :body (.getMessage e)}))))

(defroutes app
  (GET "/query/:encoded-query"
       {{encoded-query :encoded-query} :params
        components :witan.gateway.components.server/components}
       (handle-query encoded-query components)))
