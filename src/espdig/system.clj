(ns espdig.system
  (:require [com.stuartsierra.component :as component]
            ;;
            [espdig.components.db :refer [make-db]]
            [espdig.components.aws :refer [make-aws-connection]]
            [espdig.components.youtube-downloader :refer [make-youtube-downloader]]
            [espdig.components.youtube-feeds :refer [make-youtube-feeds-checker]]))

(def youtube-feeds
  ["https://www.youtube.com/feeds/videos.xml?channel_id=UCfeeUuW7edMxF3M_cyxGT8Q"])

(defn new-system
  []
  (let [config {:db {:host "127.0.0.1"
                     :port 28015
                     :db-name "test"}
                :aws {:profile "espdig"}
                :media {:tbl-name "media"}}]
    (component/system-map
     :db    (make-db (:db config))
     :aws   (make-aws-connection (:aws config))
     :feeds (component/using
             (make-youtube-feeds-checker youtube-feeds (:media config))
             [:db])
     :yt-dl (component/using
             (make-youtube-downloader (:media config))
             [:aws :db]))))
