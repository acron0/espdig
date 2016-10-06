(ns espdig.components.aws
  (:use [amazonica.aws.s3]
        [amazonica.aws.s3transfer])
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.core :as t]))

(defn upload-file
  [aws bucket key file metadata]
  (let [started (t/now)]
    (put-object aws
                :bucket-name bucket
                :key key
                :file file
                :metadata metadata)
    (log/debug "Upload took" (t/in-seconds (t/interval started (t/now))) "secs -" key)))

(defn get-file
  [aws bucket key]
  (try
    (get-object aws
                :bucket-name bucket
                :key key)
    (catch com.amazonaws.services.s3.model.AmazonS3Exception e
      (if (= (.getStatusCode e) 404)
        nil
        (log/error "Amazon error:" e)))))

(defrecord Amazon []
  component/Lifecycle
  (start [component]
    (log/info "Connecting to AWS")
    (let [conn 123]
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Disconnecting from AWS")
    (dissoc component :connection)))

(defn make-aws-connection
  [config]
  (map->Amazon config))
