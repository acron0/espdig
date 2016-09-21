(ns espdig.components.json-dumper
  (:require [com.stuartsierra.component :as component]
            [espdig.components.db :as db]
            [espdig.components.aws :as aws]
            [taoensso.timbre :as log]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [espdig.utils :refer [make-loop! stop-loop!]]
            [cheshire.core :as json]))

(def json-keys
  [:media/id
   :media/name
   :media/author
   :media/channel-id
   :media/published-at
   :video/url
   :video/thumb
   :audio/url])

(def last-json (atom nil))

;;
(defn check-for-updates
  [_ tbl-name s3-bucket filename db aws]
  (when-let [rows (not-empty (db/select-indexed-items db tbl-name :audio/status :complete))]
    (let [json (json/generate-string {:media/list (map #(select-keys % json-keys) rows)})]
      (when (not= @last-json json)
        (reset! last-json json)
        (log/info "New JSON media list produced, with" (count rows) "item(s)")
        (let [f (fs/temp-file "espdig-json")]
          (spit f json)
          (aws/upload-file aws s3-bucket filename f))))))

(defrecord JsonDumper [filename tbl-name s3-bucket]
  component/Lifecycle
  (start [{:keys [aws db] :as component}]
    (log/info "Starting JSON dumper...")
    (assoc component :loop (make-loop! {} check-for-updates
                                       tbl-name s3-bucket filename db aws)))

  (stop [component]
    (log/info "Stopping JSON dumper...")
    (let [{:keys [loop]} component]
      (stop-loop! loop)
      (dissoc component :loop))))

(defn make-json-dumper
  [config]
  (map->JsonDumper config))
