(ns espdig.components.db
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [rethinkdb.query :as rq]
            [rethinkdb.core :as rc]))

(defn connect-to-database
  [host port db]
  (rc/connect :host host :port port :db db))

(defn close-connection
  [conn]
  (rc/close conn))

(defn table-exists?
  [{:keys [connection]} tbl-name]
  (let [tables (-> (rq/table-list)
                   (rq/run connection))]
    (>= (.indexOf tables tbl-name) 0)))

(defn create-table!
  [{:keys [connection db]} tbl-name]
  (-> (rq/db db)
      (rq/table-create tbl-name)
      (rq/run connection)))

(defn create-index!
  [{:keys [connection db]} tbl-name index-key]
  (-> (rq/db db)
      (rq/table tbl-name)
      (rq/index-create (name index-key) (rq/fn [row]
                                          (rq/get-field row index-key)))
      (rq/run connection)))

(defrecord Database [host port db]
  component/Lifecycle
  (start [component]
    (log/info "Starting database")
    (let [conn (connect-to-database host port db)]
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Stopping database")
    (close-connection (:connection component))
    (assoc component :connection nil)))

(defn make-db
  [host port db]
  (->Database host port db))
