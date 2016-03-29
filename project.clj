(defproject espdig "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.2.374"]
                 [com.stuartsierra/component "0.3.1"]
                 [me.raynes/conch "0.8.0"]
                 [com.apa512/rethinkdb "0.11.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [clj-tagsoup/clj-tagsoup "0.3.0"]
                 [me.raynes/fs "1.4.6"]]
  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :source-paths ["dev"]}})
