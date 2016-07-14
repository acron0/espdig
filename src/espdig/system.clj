(ns espdig.system
  (:require [com.stuartsierra.component :as component]
            ;;
            [espdig.components.db :refer [make-db]]
            [espdig.components.aws :refer [make-aws-connection]]
            [espdig.components.youtube-downloader :refer [make-youtube-downloader]]
            [espdig.components.youtube-feeds :refer [make-youtube-feeds-checker]]
            [espdig.components.server :refer [make-http-server]]))

(def youtube-feeds
  [{:feed/channel   "Thooorin"
    :feed/rss       "https://www.youtube.com/feeds/videos.xml?channel_id=UCfeeUuW7edMxF3M_cyxGT8Q"
    :feed/title-rgx [".*"]}
   {:feed/channel   "Splyce"
    :feed/rss       "https://www.youtube.com/feeds/videos.xml?channel_id=UC30f1UTFNXfcGcrsojwOpSw"
    :feed/title-rgx ["^Spy Cam"]}])

(defn new-system
  []
  (let [config {:db {:host "127.0.0.1"
                     :port 28015
                     :db-name "test"}
                :aws {:profile "espdig"
                      :endpoint "eu-west-1"}
                :media {:tbl-name "media"
                        :s3-bucket "espdig-m4a"}
                :http {:port 8081}}]
    (component/system-map
     :db    (make-db (:db config))
     :aws   (make-aws-connection (:aws config))
     ;;:http  (make-http-server (:http config))
     :feeds (component/using
             (make-youtube-feeds-checker youtube-feeds (:media config))
             [:db])
     :yt-dl (component/using
             (make-youtube-downloader (:media config))
             [:aws :db]))))
