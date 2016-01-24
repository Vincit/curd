(ns curd.utils
  (:require [camel-snake-kebab.core :as csk]))

(defn ->kebab-case [m]
  "Transforms keywords of map to kebab-case"
  (-> (map #(-> %1 first csk/->kebab-case) m)
      (zipmap (vals m))))

(def ^:const generic-fail-message
  " crud method failed. Check the SQL Exception description above")

(defn fail [method]
  "Throws generic exception."
  (throw (Exception. (str method generic-fail-message))))