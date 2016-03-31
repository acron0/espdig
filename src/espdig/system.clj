(ns espdig.system
  (:require [com.stuartsierra.component :as component]
            ;;
            [espdig.components.db :refer [make-db]]
            [espdig.components.youtube-downloader :refer [make-youtube-downloader]]))

(def youtube-feeds
  ["https://www.youtube.com/feeds/videos.xml?channel_id=UCfeeUuW7edMxF3M_cyxGT8Q"])

(defn new-system
  []
  (let [config {:db {:host "127.0.0.1"
                     :port 28015
                     :name "test"}}]
    (component/system-map
     :db    (make-db (:db config))
     :yt-dl (component/using
             (make-youtube-downloader youtube-feeds)
             [:db]))))
