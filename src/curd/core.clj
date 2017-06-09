(ns curd.core
  (:use [curd.utils])
  (:require [clojure.java.jdbc :as j]
            [clojure.spec.alpha :as s]
            [curd.spec.core :as spec]))


(def ->dash #(.replace % \_ \-))

(def ->underscore #(.replace % \- \_))

;; ================ DB functions =======================

(defn get-conn [conn-or-spec]
  "Returns either value of :spec key, or original connection object if it has no :spec key."
  (or (:spec conn-or-spec) conn-or-spec))

(s/fdef get-conn
  :args (s/cat :conn-or-spec ::spec/conn-or-spec)
  :ret (s/cat :conn ::spec/conn))

(defn insert!
  "Wrapper for java.jdbc's insert! function.
  Input connection can be either db's spec or transaction.

  entities-fn transforms columns' and tables' names to desired db format (default is ->underscore)

  Inserts record and returns it back. Keywords are converted to clojure format.
  Supports creation of multiple rows, if supplied data is vector."
  [{:keys [conn-or-spec table data entities-fn]
    :or {entities-fn identity}}]
  (if (map? data)
    (->> (j/insert! (curd.core/get-conn conn-or-spec) table data {:entities entities-fn})
         first
         ->kebab-case)
    (->> (j/insert-multi! (curd.core/get-conn conn-or-spec) table data {:entities entities-fn})
         (map #(->kebab-case %)))))

(s/fdef insert!
  :args (s/? ::spec/insert!-args)
  :ret (s/or :map map?
             :vector vector?
             :seq seq?))

(defn do-query
  "Wrapper for java.jdbc's query function.
  Input connection can be either db's spec or transaction.
  Takes optional result-set-fn and row-fn processing functions."
  [{:keys [conn-or-spec query result-set-fn row-fn]}]
  (j/query (curd.core/get-conn conn-or-spec) query {:identifiers   ->dash
                                                    :result-set-fn (or result-set-fn doall)
                                                    :row-fn        (or row-fn identity)}))

(s/fdef do-query
  :args (s/? ::spec/do-query-args)
  :ret (s/or :vector vector?
             :map map?
             :seq seq?))

(defn execute!
  "Wrapper for java.jdbc's execute! function.
  Input connection can be either db's spec or transaction"
  [{:keys [conn-or-spec query]}]
  (j/execute! (curd.core/get-conn conn-or-spec) query))

(s/fdef execute!
  :args (s/? ::spec/execute!-args))

(defn delete!
  "Wrapper for java.jdbc's delete! function.
  Inputs are db's spec or transaction, table and sql query
  with parameters."
  [{:keys [conn-or-spec table query]}]
  (j/delete! (curd.core/get-conn conn-or-spec) table query))

(s/fdef delete!
  :args (s/? ::spec/delete!-args))

(defn find-one-by-id
  "Wrapper for java.jdbc's get-by-id function.
  Input connection can be either db's spec or transaction.
  Also function takes required table and private key value,
  as well as optional private key name (default is :id) and data set processing functions."
  [{:keys [conn-or-spec table key-value key-name result-set-fn entities-fn identifiers-fn]}]
  (j/get-by-id (curd.core/get-conn conn-or-spec) table key-value (or key-name :id) {:result-set-fn (or result-set-fn identity)
                                                                                    :entities      (or entities-fn identity)
                                                                                    :identifiers   (or identifiers-fn identity)}))

(s/fdef find-one-by-id
  :args (s/? ::spec/find-one-by-id-args)
  :ret (s/? map?))

(defmacro in-transaction
  "Wraps java.jdbc's with-db-transcation macro. The first input is binding, providing database connection for the
  transaction and the name to which that is bound for evaluation of the body. The binding may also specify
  the isolation level for the transaction, via the :isolation option and/or set the transaction to
  readonly via the :read-only? option.

  (in-transaction [conn db {:read-only? true}]
    (....body....))"
  [[conn db & params] & body]
  (let [binding ['conn '(curd.core/get-conn db) (first params)]]
    `(j/with-db-transaction ~binding ~@body)))

;; ================ New CRUD Method Macro ==================

(defmulti do! :method)

(defmacro defcrudmethod
  "Adds new method to do! multimethod.

  Inputs:

  method  - name of method (keyword)
  doc     - docstring
  arglist - arguments (with destructuring in format [{:keys [db table data] :as input}]
  body    - function to execute

  Exceptions are handled automatically.
  Notice, that arglist should have :as alias in order to display useful information when exception is thrown."
  [method doc arglist & body]
  (let [kw (->namespaced-keyword method)]
    `(defmethod do! ~kw ~(vec arglist) ~@doc
       (try
          ~@body
          (catch Exception ex#
            (fail ~kw ex# (:as ~@arglist)))))))
;
(s/fdef defcrudmethod
  :args (s/cat :method-name keyword? :doc string? :arguments vector? :body any?)
  :ret ::spec/multi-fn)

;; ================ Basic CRUD API ==================

(defcrudmethod ::create!
  "Inserts single row to database and returns created row."
  [{:keys [db table data] :as input}]
  (insert! {:conn-or-spec db
            :table        table
            :data         data
            :entities-fn  ->underscore}))

(defcrudmethod ::find-all
  "Executes specified query and returns all result rows."
  [{:keys [db query result-set-fn row-fn] :as input}]
  (do-query {:conn-or-spec   db
             :query          query
             :result-set-fn  (or result-set-fn doall)
             :row-fn         (or row-fn identity)}))

(defcrudmethod ::find-one-by-id
  "Executes a simple find-one-by-id query without need to generate custom sql query."
  [{:keys [db table key-value key-name result-set-fn entities-fn identifiers-fn] :as input}]
  (find-one-by-id  {:conn-or-spec   db
                    :table          table
                    :key-value      key-value
                    :key-name       key-name
                    :entities-fn    (or entities-fn ->underscore)
                    :result-set-fn  (or result-set-fn identity)
                    :identifiers-fn (or identifiers-fn ->dash)}))

(defcrudmethod ::find-one
  "Executes specified query and returns only first row.
  Assumes that query is designed in a way that it returns only one row.
  Should be used for queries by id or some other unique identifier."
  [{:keys [db query row-fn] :as input}]
  (do-query {:conn-or-spec   db
             :query          query
             :row-fn         (or row-fn identity)
             :result-set-fn  first}))

(defcrudmethod ::update!
  "Updates data based on specified query.
  Returns a sequence of the number of rows updated."
  [{:keys [db query] :as input}]
  (in-transaction [conn db]
    (execute! {:conn-or-spec  conn
               :query         query})))

(defcrudmethod ::delete!
  "Deletes data from table based on specified query."
  [{:keys [db table query] :as input}]
  (delete! {:conn-or-spec db
            :table        table
            :query        query}))

(defcrudmethod ::update-or-insert!
  "Updates row if it exists or creates new."
  [{:keys [db table data query] :as input}]
  (in-transaction [conn db {:read-only? false}]
    (let [existing (do! {:method ::find-one
                       :db     conn
                       :query  query})]
      (if (empty? existing)
        (do! {:method ::create!
              :db     conn
              :table  table
              :data   data})
        data))))