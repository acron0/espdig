(ns espdig.components.db
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [rethinkdb.query :as rq]
            [rethinkdb.core :as rc]))

(defn deflate-key
  [m]
  (-> m
      (str)
      (subs 1)
      (clojure.string/replace "/" "__")
      (keyword)))

(defn inflate-key
  [m]
  (-> m
      (str)
      (subs 1)
      (clojure.string/replace "__" "/")
      (keyword)))

(defn deflate-map
  [m]
  (reduce-kv (fn [a k v] (assoc a (deflate-key k) v)) {} m))

(defn inflate-map
  [m]
  (reduce-kv (fn [a k v] (assoc a (inflate-key k) v)) {} m))

(defn connect-to-database
  [host port db-name]
  (rc/connect :host host :port port :db db-name))

(defn close-connection
  [conn]
  (rc/close conn))

(defn table-exists?
  [{:keys [connection]} tbl-name]
  (let [tables (-> (rq/table-list)
                   (rq/run connection))]
    (>= (.indexOf tables tbl-name) 0)))

(defn create-table!
  [{:keys [connection db-name]} tbl-name]
  (-> (rq/db db-name)
      (rq/table-create tbl-name)
      (rq/run connection)))

(defn create-index!
  [{:keys [connection db-name]} tbl-name index-key]
  (let [index-key' (deflate-key index-key)]
    (-> (rq/db db-name)
        (rq/table tbl-name)
        (rq/index-create (name index-key') (rq/fn [row]
                                             (rq/get-field row index-key')))
        (rq/run connection))))

(defn insert-item!
  [{:keys [connection db-name]} tbl-name item]
  (let [item' (deflate-map item)]
    (-> (rq/db db-name)
        (rq/table tbl-name)
        (rq/insert item')
        (rq/run connection))))

(defn update-item!
  [{:keys [connection db-name]} tbl-name id key val]
  (-> (rq/db db-name)
      (rq/table tbl-name)
      (rq/get id)
      (rq/update (rq/fn [item]
                   {(deflate-key key) val}))
      (rq/run connection)))

(defn get-item-by-id
  [{:keys [connection db-name]} tbl-name id]
  (-> (rq/db db-name)
      (rq/table tbl-name)
      (rq/get id)
      (rq/run connection)))

(defn select-indexed-items
  [{:keys [connection db-name]} tbl-name index value]
  (mapv inflate-map
        (-> (rq/db db-name)
            (rq/table tbl-name)
            (rq/get-all [value] {:index (deflate-key index)})
            (rq/run connection))))

(defrecord Database [host port db-name]
  component/Lifecycle
  (start [component]
    (log/info "Starting database")
    (let [conn (connect-to-database host port db-name)]
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Stopping database")
    (close-connection (:connection component))
    (assoc component :connection nil)))

(defn make-db
  [args]
  (map->Database args))
