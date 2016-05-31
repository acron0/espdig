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
  (let [opts {:verbose true :timeout 120000}]
    (log/debug "SH:" (utils/fn-name rfn) (clojure.string/join " " argsv))
    (let [result (apply rfn (conj argsv opts))]
      (log/debug "SH exit-code:" (-> result :exit-code deref))
      result)))

(defn process-entry!
  [{:keys [entry chan-id author]} dir]
  (when @running?
    (let [{:keys [id original-link]} entry]
      (if false ;;(s3/exists aws id)
        (log/debug id "already exists on S3. Checking db...")
        (do
          (log/info "Downloading new video:" original-link)
          (let [output-file (str id ".m4a")
                docker-line ["run" "--net=host" "--rm" "-v" (str dir ":/src")
                             "jbergknoff/youtube-dl" "-f" "bestaudio[ext=m4a]" "-o" (str "/src/" output-file) original-link]]
            (try
              (let [result (run-shell-cmd! docker docker-line)]
                (if-not ((every-pred number? zero?) (-> result :exit-code deref))
                  (log/error "An error occurred whilst downloading the video:" result)))
              (catch Exception e (log/error e)))))))))

(defn start-loop! [db {:keys [new-entry-mult]} temp-dir]
  (let [ch (async/chan)
        tp (async/tap new-entry-mult ch)]
    (async/go-loop []
      (if-let [entry (async/<! ch)]
        (do
          (process-entry! entry temp-dir)
          (when @running?
            (recur)))
        (log/debug "Downloader loop exited.")))))

(defrecord YoutubeDownloader []
  component/Lifecycle
  (start [component]
    (let [{:keys [db feeds aws]} component]
      (log/info "Starting Youtube RSS downloader")

      ;; create temp dir
      (let [temp-dir (fs/temp-dir "video-staging")]
        (fs/chmod "+w" temp-dir)
        (log/info "Temporary directory created:" temp-dir)
        (reset! running? true)
        (start-loop! db feeds temp-dir)
        (assoc component :temp-dir temp-dir))))

  (stop [component]
    (log/info "Stopping Youtube RSS downloader")
    (when-let [temp-dir (:temp-dir component)]
      (fs/delete-dir temp-dir))
    (reset! running? false)
    (dissoc component :temp-dir)))

(defn make-youtube-downloader
  []
  (->YoutubeDownloader))
