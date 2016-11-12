(ns curd.exception
  (:require [io.aviso.exception :refer [write-exception]]))

(defn curd-exception
  [{:keys [message] :as data}]
  (let [exception (ex-info message data)]
    (write-exception exception)
    exception))