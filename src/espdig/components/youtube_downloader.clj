(ns espdig.components.youtube-downloader
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [espdig.components.aws :as aws]
            [pl.danieljanus.tagsoup :as tagsoup]
            [clojure.core.async :as async]
            [me.raynes.conch :as sh]
            [me.raynes.fs :as fs]
            [espdig.utils :as utils]
            [clojure.core.async :as async]))

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

(defn process-entry!
  [entry dir]
  (when @running?
    (let [{:keys [media/id media/channel-id video/url]} entry
          id' (str channel-id "_" id)]
      (if false ;;(s3/exists aws id)
        (log/debug id' "already exists on S3. Checking db...")
        (do
          (log/info "Downloading new video:" url)
          (let [output-file (str id' ".m4a")
                docker-line ["run" "--net=host" "--rm" "-v" (str dir ":/src")
                             "jbergknoff/youtube-dl" "-f" "bestaudio[ext=m4a]" "-o" (str "/src/" output-file) url]]
            (try
              (let [result (run-shell-cmd! docker docker-line)]
                (if-not ((every-pred number? zero?) (-> result :exit-code deref))
                  (log/error "An error occurred whilst downloading the video:" result)))
              (catch Exception e (log/error e)))))))))

(defn start-loop! [db temp-dir]
 ;; FIX THIS TO READ PENDING FROM DB
  #_(async/go-loop []
      (if-let [entry (async/<! ch)]
        (do
          (process-entry! entry temp-dir)
          (when @running?
            (recur)))
        (do
          (log/debug "Downloader loop exited.")))))

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
        (start-loop! db temp-dir)
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
