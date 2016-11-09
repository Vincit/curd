(ns curd.exception)

(defn curd-exception
  [{:keys [message] :as data}]
  (let [exception (ex-info message data)]
    (prn exception)
    exception))