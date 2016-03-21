(ns espdig.core
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [espdig.system :as sys]))

(defn -main [& args]
  (component/start
   (sys/make-system)))
