(ns espdig.components.aws
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

(defrecord Amazon []
  component/Lifecycle
  (start [component]
    (log/info "Connecting to AWS")
    (let [conn 123]
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Disconnecting from AWS")
    (dissoc component :connection)))

(defn make-aws-connection
  [config]
  (map->Amazon config))
