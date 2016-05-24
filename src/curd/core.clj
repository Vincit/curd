(ns curd.core
  (:use [curd.utils])
  (:require [clojure.java.jdbc :as j])
  (:import (java.sql SQLException)))


(def ->dash #(.replace % \_ \-))

(def ->underscore #(.replace % \- \_))

;; ================ DB functions =======================

(defn insert! [{:keys [conn table data entities-fn]
                :or {entities-fn identity}}]
  "Wrapper for java.jdbc's insert! function.
  Input conn can be either db's spec or transaction.

  entities-fn transforms columns' and table's names to desired db format.

  Inserts record and returns it back. Keywords are converted to clojure format.
  Supports creation of multiple rows, if supplied data is vector."
  (if (map? data)
    (->> (j/insert! conn table data {:entities entities-fn})
         first
         ->kebab-case)
    (->> (j/insert-multi! conn table data {:entities entities-fn})
         (map #(->kebab-case %)))))

(defn do-query [{:keys [conn query result-set-fn row-fn]}]
  "Wrapper for java.jdbc's query function.
  Input conn can be either db's spec or transaction.
  Takes optional result-set-fn and row-fn processing functions."
  (j/query conn query {:identifiers   ->dash
                       :result-set-fn (or result-set-fn doall)
                       :row-fn        (or row-fn identity)}))

(defn execute! [{:keys [conn query]}]
  "Wrapper for java.jdbc's execute! function.
  Input conn can be either db's spec or transaction"
  (j/execute! conn query))

(defn delete! [{:keys [conn table query]}]
  "Wrapper for java.jdbc's delete! function.
  Inputs are db's spec or transaction, table and sql query
  with parameters."
  (j/delete! conn table query))

(defmacro in-transaction
  [binding & body]
  `(j/with-db-transaction ~binding ~@body))

;; ================ New CRUD Method Macro ==================

(defmulti do! :method)

(defmacro defcrudmethod [method arglist & more]
  "Adds new method to do! multimethod.

  Inputs:

  method  - name of method (keyword)
  arglist - arguments
  more    - function to execute"
  (let [kw (keyword (name method))]
    `(defmethod do! ~kw ~(vec arglist) ~@more)))


;; ================ Basic CRUD API ==================

(defcrudmethod :create! [{:keys [db table data]}]
               "Inserts single row to database and returns created row."
               (try
                 (insert! {:conn        db
                           :table       table
                           :data        data
                           :entities-fn ->underscore})
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :create!))))

(defcrudmethod :find-all [{:keys [db query result-set-fn row-fn]}]
               "Executes specified query and returns all result rows."
               (try
                 (do-query {:conn           db
                            :query          query
                            :result-set-fn  result-set-fn
                            :row-fn         row-fn})
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :find-all))))

(defcrudmethod :find-one [{:keys [db query]}]
               "Executes specified query and returns only first row.
               Assumes that query is designed in a way that it returns only one row.
               Should be used for queries by id or some other unique identifier."
               (try
                 (do-query {:conn           db
                            :query          query
                            :result-set-fn  first})
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :find-one))))

(defcrudmethod :update! [{:keys [db query]}]
               "Updates data based on specified query.
               Returns a sequence of the number of rows updated."
               (try
                 (execute! {:conn  db
                            :query query})
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :update!))))

(defcrudmethod :delete! [{:keys [db table query]}]
               "Deletes data from table based on specified query."
               (try
                 (delete! {:conn  db
                           :table table
                           :query query})
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :delete!))))

(defcrudmethod :update-or-insert! [{:keys [db table data query]}]
               "Updates row if it exists or creates new."
               (try
                 (in-transaction [t-con db]
                                 (let [result (execute! {:conn  t-con
                                                         :query query})]
                                   (if (zero? (first result))
                                     (insert! {:conn        t-con
                                               :table       table
                                               :data        data
                                               :entities-fn ->underscore})
                                     data)))
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :update-or-insert!))))


;; ================ Simple public helpers  ==================

(defn prepare-create-map [db table data]
  "Prepares a map for :create! crud method"
  {:method :create!
   :db     db
   :table  table
   :data   data})

(defn prepare-query-map [db method sql]
  "Prepares a map for any query crud method"
  {:method  method
   :db      db
   :query   sql})

(defn prepare-delete-map [db method table sql]
  {:method  method
   :db      db
   :table   table
   :query   sql})

(defn prepare-create-or-update-map [db method table data sql]
  "Prepares a map for create or update crud method"
  {:method  method
   :table   table
   :db      db
   :query   sql
   :data    data})