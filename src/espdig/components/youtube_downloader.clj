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
  [_ db feed feed-idx]
  (log/debug "Checking feed:" feed)
  (when-let [resp (tagsoup/parse feed)]
    (hash-map :idx feed-idx
              :chan-id (-> resp (find-element :channelId) (last))
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
  (let [ended? (atom false)
        a      (agent nil)]
    (add-watch a :watcher (fn [_ _ _ state]
                            (record-entries! state)
                            (when (zero? (:idx state))
                              (reset! ended? true))))
    (doseq [[feed-idx feed] (reverse (map-indexed vector feeds))]
      (send a check-feed! db feed feed-idx))))

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
