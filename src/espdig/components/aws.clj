(ns espdig.components.aws
  (:use [amazonica.aws.s3]
        [amazonica.aws.s3transfer])
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.core :as t]))

(defn upload-file
  [aws bucket key file]
  (let [started (t/now)]
    (put-object aws
                :bucket-name bucket
                :key key
                :file file)
    (log/debug "Upload took" (t/in-seconds (t/interval started (t/now))) "s.")))

(defn get-file
  [aws bucket key]
  (get-object aws
              :bucket-name bucket
              :key key))

(defrecord Amazon [profile]
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
