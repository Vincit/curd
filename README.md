# curd

[![Build Status](https://travis-ci.org/Vincit/curd.svg?branch=master)](https://travis-ci.org/Vincit/curd)

A Clojure wrapper around [java.jdbc](https://github.com/clojure/java.jdbc) making CRUD operations hassle-free and as simple as Clojure's data structures are.
Curd is heavily customizable and library agnostic.
Usually we use the same set of CRUD methods all over again and again. Life would so much easier, if we could have a single 
interface to perform all our queries, inserts and so on, by simply passing data! After all, Clojure is all about data. That's exactly what Curd offers.

## Latest Version

[![Clojars Project](https://img.shields.io/clojars/v/vincit/curd.svg)](https://clojars.org/vincit/curd)

## [Changelog](https://github.com/Vincit/curd/blob/master/CHANGELOG.md)

## Usage

Curd doesn't know anything about underlying database. It doesn't care about the way you generate queries either. All it needs
is database's spec, sql with parameters (just as like java.jdbc) and occasionally database's table name.
For example. you can generate queries with [HoneySql](https://github.com/jkk/honeysql). 
Crud operations in curd are declarative, clean and easy to comprehend. 

Notice, that you can supply as `db` input parameter either connection, or map with `:spec` key, containing connection.

### Creating

```clj
(ns example
  (:require [curd.core :as c]))

(defn save-user [db data]
    (c/do! {:method   ::c/create!
            :db       db
            :table    :users
            :data     data}))
    
```

Supplied data to `create!` method can be map or vector of maps.

### Reading

```clj
(ns example
  (:require [curd.core :as c]))

(defn find-users [db sql-query]
    (c/do! {:method   ::c/find-all
            :db       db
            :query    sql-query}))
    
```

### Updating

```clj
(ns example
  (:require [curd.core :as c]))

(defn update-user [db sql-query]
    (c/do! {:method   ::c/update!
            :db       db
            :query    sql-query}))
    
```

### Deleting

```clj
(ns example
  (:require [curd.core :as c]))

(defn delete-user [db table sql-query]
    (c/do! {:method   ::c/delete!
            :db       db
            :table    table
            :query    sql-query}))
    
```

#### Out of the box

Curd has following methods supported out of the box: 
- `::create!`
- `::find-one`
- `::find-one-by-id`
- `::find-all`
- `::update!`
- `::update-or-insert!`

## Customizing

Not satisfied with existing methods? Just add new method to `do!` multimethod using `defcrudmethod` macro and off you go!
Here is example for `::find-one` method:

```clj
(defcrudmethod ::find-one
  "Executes specified query and returns only first row.
  Assumes that query is designed in a way that it returns only one row.
  Should be used for queries by id or some other unique identifier."
  [{:keys [db query] :as input}]
  (do-query {:conn-or-spec   db
             :query          query
             :result-set-fn  first}))
```

Notice also, that namespaced keywords are used as names for crud methods! So you can as well write crudmethod with same name in other namespace, 
and any collisions will be avoided.


### Transactions 

Transactions are handled with `in-transaction` macro, which is a wrapper around java.jdbc's `with-db-transaction` macro. It can be used like this:

```clj
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
``` 

The macro takes two parameters - binding with connection and map of optional transaction options (`:read-only?` and `:isolation`), and function to be run
in the context of transaction.

## License

Copyright © 2016 [Vincit Oy](https://www.vincit.fi/en/)

Distributed under the Eclipse Public License, the same as Clojure.
