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
                 (make-youtube-feeds-checker (:youtube-feeds config) (:media config))
                 [:db])
         :yt-dl (component/using
                 (make-youtube-downloader (:media config))
                 [:aws :db])
         :json (component/using
                (make-json-dumper (assoc (:json config)
                                         :tbl-name (get-in config [:media :tbl-name])))
                [:aws :db]))))))
