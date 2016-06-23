(ns curd.utils
  (:require [camel-snake-kebab.core :as csk]
            [clojure.spec :as s]))

(defn ->kebab-case
  "Transforms keywords of map to kebab-case"
  [m]
  (-> (map #(-> %1 first csk/->kebab-case) m)
      (zipmap (vals m))))

(def ^:const generic-fail-message
  " crud method failed. Check the SQL Exception description above")

(defn ->namespaced-keyword [x]
  (keyword (namespace x) (name x)))

(s/fdef ->namespaced-keyword
  :args (s/cat :keyword keyword?)
  :ret (s/cat :keyword keyword?))

(s/instrument #'->namespaced-keyword)

(defn fail
  "Throws generic exception."
  [method]
  (throw (Exception. (str (->namespaced-keyword method) generic-fail-message))))

(s/fdef fail
  :args (s/cat :keyword keyword?))

(s/instrument #'fail)