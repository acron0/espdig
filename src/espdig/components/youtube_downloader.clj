(ns espdig.components.youtube-downloader
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [espdig.components.aws :as aws]
            [pl.danieljanus.tagsoup :as tagsoup]
            [me.raynes.conch :as sh]
            [me.raynes.fs :as fs]
            [espdig.utils :as utils]
            [espdig.utils :refer [make-loop! stop-loop!]]))

;; we 'define' the shell programs we want to use
(sh/programs youtube-dl)

(defn run-shell-cmd!
  [rfn argsv]
  (let [opts {:verbose true :timeout 120000 :throw false}]
    (log/debug "SH:" (utils/fn-name rfn) (clojure.string/join " " argsv))
    (let [result (apply rfn (conj argsv opts))]
      (log/debug "SH exit-code:" (-> result :exit-code deref))
      result)))

(defn download-audio!
  [running? entry dir config aws]
  (when @running?
    (let [{:keys [media/id media/channel-id video/url]} entry
          id' (str channel-id "_" id)]
      (if url
        (if (aws/get-file aws (:s3-bucket config) id')
          (do
            (log/debug id' "already exists on S3.")
            [id' nil])
          (do
            (log/info "Downloading new video:" url)
            (let [output-file (str id' ".m4a")
                  output-filepath (str dir "/" output-file)
                  ytdl-line ["-f" "140" "-o" output-filepath url]]
              (try
                (let [result (run-shell-cmd! youtube-dl ytdl-line)]
                  (if-not ((every-pred number? zero?) (-> result :exit-code deref))
                    (when @running?
                      (log/error "An error occurred whilst downloading the video:" result))
                    [id' output-filepath]))
                (catch Exception e (log/error e))))))
        (log/error "NO URL???" entry)))))

(defn do-loop!
  [{:keys [running?]} db temp-dir config aws]
  (let [pending (db/select-indexed-items db (:tbl-name config) :audio/status :pending)]
    (if-not (zero? (count pending))
      (log/info (count pending) "item(s) pending for download."))
    (loop [remaining-entries pending]
      (when @running?
        (when-let [entry (first remaining-entries)]
          (try
            (let [{:keys [media/name media/author]} entry
                  filename (format "[%s] - %s.m4a" author name)
                  [id uri] (download-audio! running? entry temp-dir config aws)]
              (when uri
                (log/info "Uploading" uri "as" filename "| id=" id)
                (aws/upload-file aws (:s3-bucket config) filename (io/file uri)))
              (db/update-item! db (:tbl-name config) id :audio/status :complete)
              (db/update-item! db (:tbl-name config) id :audio/url (format "%s/%s/%s"
                                                                           (:s3-url config)
                                                                           (:s3-bucket config)
                                                                           filename)))
            (catch Exception e (log/error e)))
          (recur (next remaining-entries)))))))

(defrecord YoutubeDownloader [config]
  component/Lifecycle
  (start [component]
    (let [{:keys [db aws]} component]
      (log/info "Starting Youtube RSS downloader")

      ;; create temp dir
      (let [temp-dir (fs/temp-dir "espdig-videos")]
        (fs/chmod "+w" temp-dir)
        (log/info "Temporary directory created:" temp-dir)
        (-> component
            (assoc :temp-dir temp-dir
                   :loop (make-loop! {} do-loop! db temp-dir config aws))))))

  (stop [component]
    (log/info "Stopping Youtube RSS downloader")
    (when-let [temp-dir (:temp-dir component)]
      (fs/delete-dir temp-dir))
    (let [{:keys [loop]} component]
      (stop-loop! loop))
    (dissoc component :loop :temp-dir)))

(defn make-youtube-downloader
  [config]
  (->YoutubeDownloader config))
