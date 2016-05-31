(ns espdig.components.youtube-feeds
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [espdig.components.aws :as aws]
            [pl.danieljanus.tagsoup :as tagsoup]
            [clojure.core.async :as async]))

(def tbl-name "yt_videos")
(def running? (atom false))
(def new-entry-ch (atom nil))

(defn create-schema!
  [db]
  ;; create table
  (db/create-table! db tbl-name)
  ;; add index
  #_(db/create-index! db tbl-name :id))

(defn find-element
  [resp k]
  (some #(when (and (vector? %) (= (first %) k)) %) resp))

(defn fetch-feed-entries!
  [db feed]
  (log/debug "Checking feed:" feed)
  (try
    (when-let [resp (tagsoup/parse feed)]
      (hash-map :chan-id (-> resp (find-element :channelId) (last))
                :author  (-> resp (find-element :author) (find-element :name) (last))
                :entries (map #(hash-map :id (-> % (find-element :videoId) (last))
                                         :title (-> % (find-element :title) (last))
                                         :original-link (-> % (find-element :link) (second) :href)
                                         :thumbnail (-> % (find-element :group) (find-element :thumbnail) (second) :url))
                              (filter #(and (vector? %) (= (first %) :entry)) resp))))
    (catch java.net.UnknownHostException _ (log/errorf "Couldn't find %s - do you have an internet connection?" feed))))

(defn publish-entry
  [pub-ch entry chan-id author]
  #_(log/debug "Publishing new feed" entry chan-id author)
  (async/put! pub-ch {:entry entry :chan-id chan-id :author author}))

(defn start-loop!
  [pub-ch db feeds]
  (async/go-loop []
    (loop [remaining-feeds feeds]
      (when @running?
        (when-let [feed (first remaining-feeds)]
          (when-let [entries (fetch-feed-entries! db feed)]
            (let [{:keys [entries chan-id author]} entries]
              (run! #(publish-entry pub-ch % chan-id author) entries)))
          (recur [(next remaining-feeds)]))))
    (when @running?
      (Thread/sleep 10000)) ;; 10 secs
    (when @running?
      (recur))))

(defrecord YoutubeFeedsChecker [feeds]
  component/Lifecycle
  (start [component]
    (let [{:keys [db]} component
          _            (reset! new-entry-ch (async/chan))
          new-entry-mult (async/mult @new-entry-ch)]
      (log/info "Starting Youtube RSS Feed Checker")
      (when-not (db/table-exists? db tbl-name)
        (log/infof "Couldn't find table '%s' - creating..." tbl-name)
        (create-schema! db))
      (reset! running? true)
      (start-loop! @new-entry-ch db feeds)
      (-> component
          (assoc :new-entry-mult new-entry-mult))))

  (stop [component]
    (log/info "Stopping Youtube RSS Feed Checker")
    (reset! running? false)
    (when @new-entry-ch
      (async/close! @new-entry-ch)
      (reset! new-entry-ch nil))
    (dissoc component :new-entry-mult)))

(defn make-youtube-feeds-checker
  [feeds]
  (->YoutubeFeedsChecker feeds))
