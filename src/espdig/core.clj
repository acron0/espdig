(ns espdig.core
  (:require [com.stuartsierra.component :as component]
            [espdig.system :refer [new-system]]
            [taoensso.timbre :as log])
  (:gen-class))

(defn -main
  "I don't do a whole lot."
  []
  (log/info "Starting system...")
  (component/start
   (new-system))
  (log/info "System running.")
  (while true))
