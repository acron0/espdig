(ns espdig.components.youtube-downloader
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [pl.danieljanus.tagsoup :as tagsoup]
            [clojure.core.async :as async]))

(def tbl-name "yt_videos")
(def running? (atom false))

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

(defn record-entries!
  [{:keys [entries] :as foo}]
  (doseq [entry entries]
    (println "GOT ENTRY" (:thumbnail entry))))

(defn start-loop!
  [db feeds]
  (async/go-loop []
    (loop [remaining-feeds feeds]
      (when-let [feed (first remaining-feeds)]
        (let [{:keys [chan-id author entries]} (fetch-feed-entries! db feed)]
          (doseq [entry entries]
            (println "Found entry" (:id entry))))
        (recur [(next remaining-feeds)])))
    (Thread/sleep 10000) ;; 10 secs
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
