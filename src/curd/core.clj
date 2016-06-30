(ns curd.core
  (:use [curd.utils])
  (:require [clojure.java.jdbc :as j]
            [clojure.spec :as s]
            [clojure.spec.test :as stest]
            [curd.spec.core :as spec])
  (:import (java.sql SQLException)))


(def ->dash #(.replace % \_ \-))

(def ->underscore #(.replace % \- \_))

;; ================ DB functions =======================

(defn get-conn [conn-or-spec]
  "Returns either value of :spec key, or original conenction object if it has no :spec key."
  (or (:spec conn-or-spec) conn-or-spec))

(defn insert!
  "Wrapper for java.jdbc's insert! function.
  Input conn can be either db's spec or transaction.

  entities-fn transforms columns' and table's names to desired db format.

  Inserts record and returns it back. Keywords are converted to clojure format.
  Supports creation of multiple rows, if supplied data is vector."
  [{:keys [conn-or-spec table data entities-fn]
    :or {entities-fn identity}}]
  (if (map? data)
    (->> (j/insert! (get-conn conn-or-spec) table data {:entities entities-fn})
         first
         ->kebab-case)
    (->> (j/insert-multi! (get-conn conn-or-spec) table data {:entities entities-fn})
         (map #(->kebab-case %)))))

(s/fdef insert!
  :args (s/? ::spec/insert!-args)
  :ret (s/or :map map?
             :vector vector?
             :seq seq?))

(stest/instrument `insert!)

(defn do-query
  "Wrapper for java.jdbc's query function.
  Input conn can be either db's spec or transaction.
  Takes optional result-set-fn and row-fn processing functions."
  [{:keys [conn-or-spec query result-set-fn row-fn]}]
  (j/query (get-conn conn-or-spec) query {:identifiers   ->dash
                                          :result-set-fn (or result-set-fn doall)
                                          :row-fn        (or row-fn identity)}))

(s/fdef do-query
  :args (s/? ::spec/do-query-args)
  :ret (s/or :vector vector?
             :map map?
             :seq seq?))

(stest/instrument `do-query)

(defn execute!
  "Wrapper for java.jdbc's execute! function.
  Input conn can be either db's spec or transaction"
  [{:keys [conn-or-spec query]}]
  (j/execute! (get-conn conn-or-spec) query))

(s/fdef execute!
  :args (s/? ::spec/execute!-args))

(stest/instrument `execute!)

(defn delete!
  "Wrapper for java.jdbc's delete! function.
  Inputs are db's spec or transaction, table and sql query
  with parameters."
  [{:keys [conn-or-spec table query]}]
  (j/delete! (get-conn conn-or-spec) table query))

(s/fdef delete!
  :args (s/? ::spec/delete!-args))

(stest/instrument `delete!)

(defn find-one-by-id
  "Wrapper for java.jdbc's get-by-id function.
  Inputs are conn, required table and private key value,
  as well as optional private key name (default is :id) and data set processing functions."
  [{:keys [conn-or-spec table key-value key-name result-set-fn entities-fn identifiers-fn]}]
  (j/get-by-id (get-conn conn-or-spec) table key-value (or key-name :id) {:result-set-fn (or result-set-fn identity)
                                                                          :entities      (or entities-fn identity)
                                                                          :identifiers   (or identifiers-fn identity)}))

(s/fdef find-one-by-id
  :args (s/? ::spec/find-one-by-id-args)
  :ret (s/? map?))

(stest/instrument `find-one-by-id)

(defmacro in-transaction
  "Wraps java.jdbc's with-db-transcation macro. The first input is binding, providing database connection for the
  transaction and the name to which that is bound for evaluation of the body. The binding may also specify
  the isolation level for the transaction, via the :isolation option and/or set the transaction to
  readonly via the :read-only? option.

  (in-transaction [conn db {:read-only? true}]
    (....body....))"
  [[conn db & params] & body]
  (let [binding ['conn '(get-conn db) (first params)]]
    `(j/with-db-transaction ~binding ~@body)))

(defn print-sql-exception-chain [e]
  (j/print-sql-exception-chain e))

;; ================ New CRUD Method Macro ==================

(defmulti do! :method)

(defmacro defcrudmethod
  "Adds new method to do! multimethod.

  Inputs:

  method  - name of method (keyword)
  doc     - docstring
  arglist - arguments
  more    - function to execute"
  [method doc arglist & more]
  (let [kw (->namespaced-keyword method)]
    `(defmethod do! ~kw ~(vec arglist) ~@doc ~@more)))
;
(s/fdef defcrudmethod
  :args (s/cat :method-name keyword? :doc string? :arguments vector? :body ::s/any)
  :ret ::spec/multi-fn)

;; ================ Basic CRUD API ==================

(defcrudmethod ::create!
  "Inserts single row to database and returns created row."
  [{:keys [db table data]}]
  (try
    (in-transaction [conn db]
      (insert! {:conn-or-spec conn
                :table        table
                :data         data
                :entities-fn  ->underscore}))
    (catch SQLException e
      (print-sql-exception-chain e)
      (fail ::create!))))

(defcrudmethod ::find-all
  "Executes specified query and returns all result rows."
  [{:keys [db query result-set-fn row-fn]}]
  (try
    (do-query {:conn-or-spec   db
               :query          query
               :result-set-fn  (or result-set-fn doall)
               :row-fn         (or row-fn identity)})
    (catch SQLException e
      (print-sql-exception-chain e)
      (fail ::find-all))))

(defcrudmethod ::find-one-by-id
  "Executes a simple find-one-by-id query without need to generate custom sql query."
  [{:keys [db table key-value key-name result-set-fn entities-fn identifiers-fn]}]
  (try
    (find-one-by-id  {:conn-or-spec   db
                      :table          table
                      :key-value      key-value
                      :key-name       key-name
                      :entities-fn    (or entities-fn ->underscore)
                      :result-set-fn  (or result-set-fn identity)
                      :identifiers-fn (or identifiers-fn ->dash)})
    (catch SQLException e
      (print-sql-exception-chain e)
      (fail ::find-one-by-id))))

(defcrudmethod ::find-one
  "Executes specified query and returns only first row.
  Assumes that query is designed in a way that it returns only one row.
  Should be used for queries by id or some other unique identifier."
  [{:keys [db query]}]
  (try
    (do-query {:conn-or-spec   db
               :query          query
               :result-set-fn  first})
    (catch SQLException e
      (print-sql-exception-chain e)
      (fail ::find-one))))

(defcrudmethod ::update!
  "Updates data based on specified query.
  Returns a sequence of the number of rows updated."
  [{:keys [db query]}]
  (try
    (in-transaction [conn db]
      (execute! {:conn-or-spec  conn
                 :query         query}))
    (catch SQLException e
      (print-sql-exception-chain e)
      (fail ::update!))))

(defcrudmethod ::delete!
  "Deletes data from table based on specified query."
  [{:keys [db table query]}]
  (try
    (delete! {:conn-or-spec db
              :table        table
              :query        query})
    (catch SQLException e
      (print-sql-exception-chain e)
      (fail ::delete!))))

(defcrudmethod ::update-or-insert!
  "Updates row if it exists or creates new."
  [{:keys [db table data query]}]
  (try
    (in-transaction [conn db {:read-only? false}]
      (let [result (do-query {:conn-or-spec   conn
                              :query          query
                              :result-set-fn  first})]
        (if-not result
          (insert! {:conn-or-spec conn
                    :table        table
                    :data         data
                    :entities-fn  ->underscore})
          data)))
    (catch SQLException e
      (print-sql-exception-chain e)
      (fail ::update-or-insert!))))


;; ================ Simple public helpers  ==================

(defn prepare-create-map
  "Prepares a map for ::create! crud method"
  [db table data]
  {:method ::create!
   :db     db
   :table  table
   :data   data})

(defn prepare-query-map
  "Prepares a map for any query crud method"
  [db method sql]
  {:method  method
   :db      db
   :query   sql})

(defn prepare-delete-map [db method table sql]
  {:method  method
   :db      db
   :table   table
   :query   sql})