(ns espdig.components.youtube-downloader
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [pl.danieljanus.tagsoup :as tagsoup]))

(def tbl-name "yt_videos")
(def running? (atom false))

(defn find-element
  [resp k]
  (some #(when (and (vector? %) (= (first %) k)) %) resp))

(defn check-feed!
  [db feed]
  (log/debug "Checking feed:" feed)
  (when-let [resp (tagsoup/parse feed)]
    (hash-map :chan-id (-> resp (find-element :channelId) (last))
              :author  (-> resp (find-element :author) (find-element :name) (last))
              :entries (map #(hash-map :id (-> % (find-element :videoId) (last))
                                       :title (-> % (find-element :title) (last))
                                       :original-link (-> % (find-element :link) (second) :href))
                            (filter #(and (vector? %) (= (first %) :entry)) resp))))

  (defn start-loop!
    [db feeds]
    (map (partial check-feed! db) feeds)))

(defrecord YoutubeDownloader [feeds]
  component/Lifecycle
  (start [{:keys [db] :as component}]
    (log/info "Starting Youtube RSS downloader")
    (when-not (db/table-exists? db tbl-name)
      (log/infof "Couldn't find table '%s' - creating..." tbl-name)
      (db/create-table! db tbl-name))
    (reset! running? true)
    (start-loop! db feeds)
    component)

  (stop [component]
    (log/info "Stopping Youtube RSS downloader")
    (reset! running? false)
    component))

(defn make-youtube-downloader
  [feeds]
  (->YoutubeDownloader feeds))
