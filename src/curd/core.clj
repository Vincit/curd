(ns curd.core
  (:use [curd.utils])
  (:require [clojure.java.jdbc :as j])
  (:import (java.sql SQLException)))


(def ->dash #(.replace % \_ \-))

(def ->underscore #(.replace % \- \_))

;; ================ DB functions =======================

(defn insert! [conn table data entities-fn]
  "Wrapper for java.jdbc's insert! function.
  Input conn can be either db's spec or transaction.

  entities-fn transforms columns' and table's names to desired db format.

  Inserts record and returns it back. Keywords are converted to clojure format."
  (let [result (j/insert! conn table data :entities (or entities-fn identity))]
    (condp = (count result)
           0 []
           1 (->> result
                  first
                  ->kebab-case)
           2 (->> result
                  (map ->kebab-case)
                  vec))))

(defn do-query [{:keys [conn query result-set-fn row-fn]}]
  "Wrapper for java.jdbc's query function.
  Input conn can be either db's spec or transaction.
  Takes optional result-set-fn and row-fn processing functions."
  (j/query conn query :identifiers ->dash
           :result-set-fn (or result-set-fn identity)
           :row-fn (or row-fn identity)))

(defn execute! [conn query]
  "Wrapper for java.jdbc's execute! function.
  Input conn can be either db's spec or transaction"
  (j/execute! conn query))

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
                 (insert! db table data ->underscore)
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :create!))))

(defcrudmethod :find-all [{:keys [db query]}]
               "Executes specified query and returns all result rows."
               (try
                 (do-query {:conn db :query query})
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :find-all))))

(defcrudmethod :find-one [{:keys [db query]}]
               "Executes specified query and returns only first row.
               Assumes that query is designed in a way that it returns only one row.
               Should be used for queries by id or some other unique identifier."
               (try
                 (do-query {:conn db :query query
                            :result-set-fn first})
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :find-one))))

(defcrudmethod :update! [{:keys [db query]}]
               "Updates data based on specified query.
               Returns a sequence of the number of rows updated."
               (try
                 (execute! db query)
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :update!))))

(defcrudmethod :update-or-insert! [{:keys [db table data query]}]
               "Updates row if it exists or creates new."
               (try
                 (in-transaction [t-con db]
                                 (let [result (execute! t-con query)]
                                   (if (zero? (first result))
                                     (insert! t-con table data ->underscore)
                                     data)))
                 (catch SQLException e
                   (j/print-sql-exception-chain e)
                   (fail :update-or-insert!))))


;; ================ Simple helpers  ==================

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

(defn prepare-create-or-update-map [db method table data sql]
  "Prepares a map for create or update crud method"
  {:method  method
   :table   table
   :db      db
   :query   sql
   :data    data})