(ns espdig.components.youtube-downloader
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [pl.danieljanus.tagsoup :as tagsoup]
            [clojure.core.async :as async]
            [me.raynes.conch :as sh]
            [me.raynes.fs :as fs]
            [espdig.utils :as utils]))

(def tbl-name "yt_videos")
(def running? (atom false))

;; we 'define' the shell programs we want to use
(sh/programs docker)

(defn find-element
  [resp k]
  (some #(when (and (vector? %) (= (first %) k)) %) resp))

(defn fetch-feed-entries!
  [db feed]
  (log/debug "Checking feed:" feed)
  (when-let [resp (tagsoup/parse feed)]
    (hash-map :chan-id (-> resp (find-element :channelId) (last))
              :author  (-> resp (find-element :author) (find-element :name) (last))
              :entries (map #(hash-map :id (-> % (find-element :videoId) (last))
                                       :title (-> % (find-element :title) (last))
                                       :original-link (-> % (find-element :link) (second) :href)
                                       :thumbnail (-> % (find-element :group) (find-element :thumbnail) (second) :url))
                            (filter #(and (vector? %) (= (first %) :entry)) resp)))))

(defn run-shell-cmd!
  [rfn argsv]
  (let [opts {:verbose true :timeout 120000}]
    (log/debug "SH:" (utils/fn-name rfn) (clojure.string/join " " argsv))
    (let [result (apply rfn (conj argsv opts))]
      (log/debug "SH exit-code:" (-> result :exit-code deref))
      result)))

(defn process-entries!
  [{:keys [entries chan-id author]} dir]
  (doseq [entry entries]
    (when @running?
      (log/info "Downloading" (:original-link entry))
      (let [{:keys [id original-link]} entry
            output-file                (str id ".m4a")
            docker-line                ["run" "--net=host" "--rm" "-v" (str dir ":/src")
                                        "jbergknoff/youtube-dl" "-f" "bestaudio[ext=m4a]" "-o" (str "/src/" output-file) original-link]]
        (try
          (let [result (run-shell-cmd! docker docker-line)]
            (if-not ((every-pred number? zero?) (-> result :exit-code deref))
              (log/error "An error occurred whilst downloading the video:" result)))
          (catch Exception e (log/error e)))))))

(defn start-loop!
  [db feeds dir]
  (async/go-loop []
    (loop [remaining-feeds feeds]
      (when @running?
        (when-let [feed (first remaining-feeds)]
          (process-entries! (fetch-feed-entries! db feed) dir)
          (recur [(next remaining-feeds)]))))
    (when @running?
      (Thread/sleep 10000)) ;; 10 secs
    (when @running?
      (recur))))

(defn create-schema!
  [db]
  ;; create table
  (db/create-table! db tbl-name)
  ;; add index
  #_(db/create-index! db tbl-name :id))

(defrecord YoutubeDownloader [feeds]
  component/Lifecycle
  (start [{:keys [db] :as component}]
    (log/info "Starting Youtube RSS downloader")
    (when-not (db/table-exists? db tbl-name)
      (log/infof "Couldn't find table '%s' - creating..." tbl-name)
      (create-schema! db))
    ;; create temp dir
    (let [temp-dir (fs/temp-dir "video-staging")]
      (fs/chmod "+w" temp-dir)
      (log/info "Temporary directory created:" temp-dir)
      (reset! running? true)
      (start-loop! db feeds temp-dir)
      (assoc component :temp-dir temp-dir)))

  (stop [component]
    (log/info "Stopping Youtube RSS downloader")
    (when-let [temp-dir (:temp-dir component)]
      (fs/delete-dir temp-dir))
    (reset! running? false)
    (dissoc component :temp-dir)))

(defn make-youtube-downloader
  [feeds]
  (->YoutubeDownloader feeds))
