(ns espdig.components.json-dumper
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clj-time.core :as t]
            [espdig.utils :refer [make-loop! stop-loop!]]
            [cheshire.core :as json]))

;;
(defn check-for-updates
  [_ filename db aws]
  (let [json-string 123]))

(defrecord JsonDumper [filename]
  component/Lifecycle
  (start [{:keys [aws db] :as component}]
    (log/info "Starting JSON dumper...")
    (assoc component :loop (make-loop! {} check-for-updates filename db aws)))

  (stop [component]
    (log/info "Stopping JSON dumper...")
    (let [{:keys [loop]} component]
      (stop-loop! loop)
      (dissoc component :loop))))

(defn make-json-dumper
  [config]
  (map->JsonDumper config))
