(ns espdig.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [aero.core :refer [read-config]]
            ;;
            [espdig.components.db :refer [make-db]]
            [espdig.components.aws :refer [make-aws-connection]]
            [espdig.components.youtube-downloader :refer [make-youtube-downloader]]
            [espdig.components.youtube-feeds :refer [make-youtube-feeds-checker]]
            [espdig.components.json-dumper :refer [make-json-dumper]]))

(def youtube-feeds
  [{:feed/channel   "Thooorin"
    :feed/rss       "https://www.youtube.com/feeds/videos.xml?channel_id=UCfeeUuW7edMxF3M_cyxGT8Q"
    :feed/title-rgx [".*"]}
   {:feed/channel   "Drop The Bomb TV"
    :feed/rss       "https://www.youtube.com/feeds/videos.xml?channel_id=UC-SmMElbXYS91yuDR1N0RIg"
    :feed/title-rgx [".*"]}
   {:feed/channel   "Richard Lewis"
    :feed/rss      "https://www.youtube.com/feeds/videos.xml?channel_id=UCEOQ9pSmMEIqfhtCDa2JORw"
    :feed/title-rgx [".*"]}
   {:feed/channel   "Monte Cristo"
    :feed/rss      "https://www.youtube.com/feeds/videos.xml?channel_id=UCZ26xCMrmYnUWYFtZr7cWCg"
    :feed/title-rgx [".*"]}
   {:feed/channel   "Insight on Esports"
    :feed/rss      "https://www.youtube.com/feeds/videos.xml?channel_id=UC4AGeYoOM9lRH6-L7wNFgTQ"
    :feed/title-rgx [".*"]}
   {:feed/channel   "Thorin's Side"
    :feed/rss      "https://www.youtube.com/feeds/videos.xml?channel_id=UC4rma8fFU0UmxiiVzH8RHDA"
    :feed/title-rgx [".*"]}])

(defn new-system
  [profile]
  (let [config (read-config (io/resource "config.edn") {:profile profile})]
    (if (and (= profile :production)
             (or (not (get-in config [:aws :aws-access-key]))
                 (not (get-in config [:aws :aws-secret-key]))))
      (log/error "Credentials missing!")
      (do
        (log/merge-config! (:log config))
        (component/system-map
         :db    (make-db (:db config))
         :aws   (make-aws-connection (:aws config))
         :feeds (component/using
                 (make-youtube-feeds-checker youtube-feeds (:media config))
                 [:db])
         :yt-dl (component/using
                 (make-youtube-downloader (:media config))
                 [:aws :db])
         :json (component/using
                (make-json-dumper (assoc (:json config)
                                         :tbl-name (get-in config [:media :tbl-name])))
                [:aws :db]))))))
