(defproject espdig "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main espdig.core
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [org.clojure/core.async "0.2.374"]
                 [me.raynes/conch "0.8.0"]
                 [com.apa512/rethinkdb "0.11.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [clj-tagsoup/clj-tagsoup "0.3.0" :exclusions [org.clojure/clojure]]
                 [me.raynes/fs "1.4.6"]
                 [prismatic/schema "1.1.1"]
                 [kixi/schema-contrib "0.2.0"]
                 [clj-time "0.11.0"]
                 [amazonica "0.3.52"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]
                   :repl-options {:init-ns user}}})
