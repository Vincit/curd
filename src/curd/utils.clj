(ns curd.utils
  (:require [camel-snake-kebab.core :as csk]))

(defn ->kebab-case
  "Transforms keywords of map to kebab-case"
  [m]
  (-> (map #(-> %1 first csk/->kebab-case) m)
      (zipmap (vals m))))

(def ^:const generic-fail-message
  " crud method failed. Check the SQL Exception description above")

(defn fail
  "Throws generic exception."
  [method]
  (throw (Exception. (str method generic-fail-message))))