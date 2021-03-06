(ns espdig.components.youtube-feeds
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [espdig.components.db :as db]
            [espdig.components.aws :as aws]
            [espdig.schema :as es]
            [pl.danieljanus.tagsoup :as tagsoup]
            [espdig.utils :refer [make-loop! stop-loop!]]))

(defn create-schema!
  [db {:keys [tbl-name]}]
  ;; create table
  (db/create-table! db tbl-name)
  (db/create-index! db tbl-name :audio/status)) ;; so we can extract pending

(defn find-element
  [resp k]
  (some #(when (and (vector? %) (= (first %) k)) %) resp))

(defn fetch-feed-entries!
  [db {:keys [feed/channel feed/rss feed/title-rgx] :as feed}]
  (log/trace "Checking feed:" feed)
  (try
    (when-let [resp (tagsoup/parse rss)]
      (hash-map :chan-id (-> resp (find-element :channelId) (last))
                :author  channel
                :entries (map #(hash-map :id (-> % (find-element :videoId) (last))
                                         :title (-> % (find-element :title) (last))
                                         :original-link (-> % (find-element :link) (second) :href)
                                         :thumbnail (-> % (find-element :group) (find-element :thumbnail) (second) :url)
                                         :published (-> % (find-element :published) (last)))
                              (filter #(and (vector? %) (= (first %) :entry)) resp))))
    (catch java.net.UnknownHostException _ (log/errorf "Couldn't find %s - do you have an internet connection?" feed))
    (catch Exception e (log/errorf "Error whilst downloading %s: %s" rss e))))

(defn save-entry!
  [config db {:keys [meta/hash] :as entry}]
  (db/insert-item! db (:tbl-name config) (assoc entry :id hash)))

(defn do-loop!
  [{:keys [running?]} db feeds config]
  (loop [remaining-feeds feeds]
    (when @running?
      (when-let [feed (first remaining-feeds)]
        (when-let [entries (fetch-feed-entries! db feed)]
          (try
            (let [{:keys [entries chan-id author]} entries
                  {:keys [feed/title-rgx]} feed
                  entries' (->> entries
                                (map #(es/entry->media-item % chan-id author))
                                (filter
                                 #(some (fn [p]
                                          (re-find (re-pattern p) (:media/name %))) title-rgx))
                                (filter
                                 #(not (db/get-item-by-id db (:tbl-name config) (:meta/hash %)))))]
              (when-not (zero? (count entries'))
                (log/info "Found" (count entries') "new entry/entries for" chan-id))
              (run! #(save-entry! config db %) entries'))
            (catch Exception e
              (log/error e))))
        (recur (next remaining-feeds))))))

(defrecord YoutubeFeedsChecker [feeds config]
  component/Lifecycle
  (start [component]
    (let [{:keys [db]} component]
      (log/info "Starting Youtube RSS Feed Checker")
      (when-not (db/table-exists? db (:tbl-name config))
        (log/infof "Couldn't find table '%s' - creating..." (:tbl-name config))
        (create-schema! db config))
      (assoc component :loop (make-loop! {} do-loop! db feeds config))))

  (stop [component]
    (log/info "Stopping Youtube RSS Feed Checker")
    (let [{:keys [loop]} component]
      (stop-loop! loop)
      (dissoc component :loop))))

(defn make-youtube-feeds-checker
  [feeds media-config]
  (->YoutubeFeedsChecker feeds media-config))
