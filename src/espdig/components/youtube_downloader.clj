(ns espdig.components.youtube-downloader
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [espdig.components.aws :as aws]
            [pl.danieljanus.tagsoup :as tagsoup]
            [clojure.core.async :as async]
            [me.raynes.conch :as sh]
            [me.raynes.fs :as fs]
            [espdig.utils :as utils]
            [clojure.core.async :as async]))

;; Time between checks
(def check-delay 10000)
(def running? (atom false))

;; we 'define' the shell programs we want to use
(sh/programs docker)

(defn run-shell-cmd!
  [rfn argsv]
  (let [opts {:verbose true :timeout 120000 :throw false}]
    (log/debug "SH:" (utils/fn-name rfn) (clojure.string/join " " argsv))
    (let [result (apply rfn (conj argsv opts))]
      (log/debug "SH exit-code:" (-> result :exit-code deref))
      result)))

(defn download-audio!
  [entry dir config aws]
  (when @running?
    (let [{:keys [media/id media/channel-id video/url]} entry
          id' (str channel-id "_" id)]
      (if url
        (if (aws/get-file aws (:s3-bucket config) id')
          (log/debug id' "already exists on S3. Checking db...")
          [id' nil]
          #_(do
              (log/info "Downloading new video:" url)
              (let [output-file (str id' ".m4a")
                    docker-line ["run" "--net=host" "--rm" "-v" (str dir ":/src")
                                 "jbergknoff/youtube-dl" "-f" "bestaudio[ext=m4a]" "-o" (str "/src/" output-file) url]]
                (try
                  (let [result (run-shell-cmd! docker docker-line)]
                    (if-not ((every-pred number? zero?) (-> result :exit-code deref))
                      (when @running?
                        (log/error "An error occurred whilst downloading the video:" result))
                      [id' (str dir "/" output-file)]))
                  (catch Exception e (log/error e))))))
        (log/error "NO URL???" entry)))))

(defn start-loop! [db temp-dir config aws]
  (async/go-loop []
    (let [pending (db/select-indexed-items db (:tbl-name config) :audio/status :pending)]
      (log/info (count pending) "item(s) pending for download.")
      (loop [remaining-entries pending]
        (when @running?
          (when-let [entry (first remaining-entries)]
            (try
              (let [[id uri] (download-audio! entry temp-dir config aws)]
                (when uri
                  (log/info "Uploading" uri "as" id)
                  (aws/upload-file aws (:s3-bucket config) id (io/file uri))))
              (catch Exception e (log/error e)))
            (recur (next remaining-entries))))))
    (when @running?
      (Thread/sleep check-delay))
    (when @running?
      (recur))))

(defrecord YoutubeDownloader [config]
  component/Lifecycle
  (start [component]
    (let [{:keys [db aws]} component]
      (log/info "Starting Youtube RSS downloader")

      ;; create temp dir
      (let [temp-dir (fs/temp-dir "video-staging")]
        (fs/chmod "+w" temp-dir)
        (log/info "Temporary directory created:" temp-dir)
        (reset! running? true)
        (start-loop! db temp-dir config aws)
        (assoc component :temp-dir temp-dir))))

  (stop [component]
    (log/info "Stopping Youtube RSS downloader")
    (when-let [temp-dir (:temp-dir component)]
      (fs/delete-dir temp-dir))
    (reset! running? false)
    (dissoc component :temp-dir)))

(defn make-youtube-downloader
  [config]
  (->YoutubeDownloader config))
