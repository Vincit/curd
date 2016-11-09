(ns curd.utils
  (:require [camel-snake-kebab.core :as csk]
            [curd.exception :as e]
            [clojure.spec :as s])
  (:import (java.sql SQLException)))

(defn ->kebab-case
  "Transforms keywords of map to kebab-case"
  [m]
  (-> (map #(-> %1 first csk/->kebab-case) m)
      (zipmap (vals m))))

(def ^:const generic-fail-message
  " crud method failed")

(defn ->namespaced-keyword [x]
  (keyword (namespace x) (name x)))

(s/fdef ->namespaced-keyword
  :args (s/cat :keyword keyword?)
  :ret  (s/cat :keyword keyword?))

(defn sql-exception-info
  [^SQLException ex]
  {:message     (.getMessage ex)
   :sql-state   (.getSQLState ex)
   :error-code  (.getErrorCode ex)})

(defn sql-exception-chain
  [^SQLException ex]
  (let [chain (atom [])]
    (loop [e ex]
      (when e
        (swap! chain conj (sql-exception-info e))
        (recur (.getNextException e))))
    @chain))

(defn fail
  "Throws exception."
  [method ex input]
  (let [ns-method (->namespaced-keyword method)]
    (throw (e/curd-exception (-> {:message              (str ns-method generic-fail-message)
                                  :method               ns-method
                                  :input                input}
                                 (merge (if (instance? SQLException ex)
                                          {:sql-exception  {:sql-exception-chain (sql-exception-chain ex)}}
                                          {:exception {:message (.getMessage ex)}})))))))

(s/fdef fail
  :args (s/cat :keyword keyword? :ex-info #(instance? Exception %) :input map?))