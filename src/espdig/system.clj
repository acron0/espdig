(ns espdig.system
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [taoensso.timbre :as log]
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
    :feed/title-rgx [".*"]}])

(defn new-system
  [profile]
  (if-not (and (env :aws-access-key) (env :aws-secret-key))
    (log/error "Credentials not found.")
    (let [_ (log/info "Credentials found.")
          config {:db {:host (if (= profile :development) "127.0.0.1" "rethink")
                       :port 28015
                       :db-name "test"}
                  :aws {:aws-access-key (env :aws-access-key)
                        :aws-secret-key (env :aws-secret-key)
                        :endpoint "eu-west-1"}
                  :media {:tbl-name "media"
                          :s3-bucket "espdig-m4a"
                          :s3-url "https://s3-eu-west-1.amazonaws.com"}
                  :json {:filename "data.json"
                         :s3-bucket "espdig-www"}}]
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
              [:aws :db])))))
