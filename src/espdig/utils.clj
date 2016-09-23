(ns espdig.utils
  (:require [clojure.core.async :as async]))

(defn fn-name
  [rfn]
  (last (re-find #"^.+\$([a-zA-z\-]+)" (str rfn))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def default-delay 10000)

(defn make-loop!
  [{:keys [delay] :or {delay default-delay}}
   fnc & args]
  (let [running? (atom true)
        ret {:running? running?}]
    (async/go-loop []
      (apply fnc (concat [ret] args))
      (when @running?
        (Thread/sleep delay))
      (when @running?
        (recur)))
    ret))

(defn stop-loop!
  [{:keys [running?]}]
  (reset! running? false))
