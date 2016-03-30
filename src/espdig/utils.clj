(ns espdig.utils)

(defn fn-name
  [rfn]
  (last (re-find #"^.+\$([a-zA-z\-]+)" (str rfn))))
