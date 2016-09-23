(ns espdig.schema
  (:require [schema.core :as s]
            [schema-contrib.core :as sc]
            [clj-time.core :as t]))

(def StatusEnum
  (s/enum
   :pending
   :complete))

(def MediaItem
  {:media/id           s/Str
   :media/name         s/Str
   :media/author       s/Str
   :media/channel-id   s/Str
   :media/published-at sc/ISO-Date-Time
   :video/url          s/Str
   :video/thumb-url    s/Str
   :audio/url          (s/maybe s/Str)
   :audio/status       StatusEnum
   :meta/created-at    sc/ISO-Date-Time
   :meta/hash          s/Str})

(defn entry->media-item
  [{:keys [original-link title thumbnail id published]} chan-id author]
  (s/validate
   MediaItem
   {:media/id            id
    :media/name          title
    :media/author        author
    :media/channel-id    chan-id
    :media/published-at  published
    :video/url           original-link
    :video/thumb-url     thumbnail
    :audio/status        :pending
    :audio/url           nil
    :meta/created-at     (str (t/now))
    :meta/hash           (str chan-id "_" id)}))
