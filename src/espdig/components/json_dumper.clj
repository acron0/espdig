(ns espdig.components.json-dumper
  (:require [com.stuartsierra.component :as component]
            [espdig.components.db :as db]
            [espdig.components.aws :as aws]
            [taoensso.timbre :as log]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clj-time.core :as t]
            [espdig.utils :refer [make-loop! stop-loop!]]
            [cheshire.core :as json])
  (:import java.util.zip.GZIPOutputStream))

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

(defn gzip
  [input output & opts]
  (with-open [output (-> output io/output-stream GZIPOutputStream.)]
    (apply io/copy input output opts)))

;;
(defn check-for-updates
  [_ tbl-name s3-bucket filename db aws]
  (when-let [rows (not-empty (db/select-indexed-items db tbl-name :audio/status :complete))]
    (let [json (json/generate-string {:media/list (map #(select-keys % json-keys) rows)})]
      (when (not= @last-json json)
        (reset! last-json json)
        (log/info "New JSON media list produced, with" (count rows) "item(s)")
        (let [f1 (fs/temp-file "espdig-json")
              f2 (fs/temp-file "espdig-json")]
          (spit f1 json)
          (with-open [in (io/input-stream f1)
                      out (io/output-stream f2)]
            (gzip in out))
          (aws/upload-file aws s3-bucket filename f2)
          (fs/delete (str f1))
          (fs/delete (str f2)))))))

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
