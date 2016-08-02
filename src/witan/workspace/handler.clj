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

#_(defn ws-handler [request]
    (let [components (:witan.gateway.components.server/components request)]
      (with-channel request channel
        (connect! channel)
        (on-close channel (partial disconnect! channel))
        (on-receive channel #(try
                               (let [msg (read-string %)
                                     error (rs/check-message "1.0" msg)]
                                 (if-not error
                                   (handle-message channel msg components)
                                   (send-edn! channel {:error error :original msg})))
                               (catch Exception e
                                 (println "Exception thrown:" e)
                                 (send-edn! channel {:error (str e) :original %})))))))



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
  ;;(GET "/ws" req (ws-handler req))
  (GET "/query/:encoded-query"
       {{encoded-query :encoded-query} :params
        components :witan.gateway.components.server/components}
       (handle-query encoded-query components)))
