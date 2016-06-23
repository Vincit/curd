# curd

[![Build Status](https://travis-ci.org/Vincit/curd.svg?branch=master)](https://travis-ci.org/Vincit/curd)

A Clojure wrapper around [java.jdbc](https://github.com/clojure/java.jdbc) making CRUD operations hassle-free and as simple as Clojure's data structures are.
Curd is heavily customizable and library agnostic.
Usually we use the same set of CRUD methods all over again and again. Life would so much easier, if we could have a single 
interface to perform all our queries, inserts and so on. That's exactly what Curd offers.

## Latest Version

[![Clojars Project](https://img.shields.io/clojars/v/vincit/curd.svg)](https://clojars.org/vincit/curd)

## Usage

Curd doesn't know anything about underlying database. It doesn't care about the way you generate queries either. All it needs
is database's spec, sql with parameters (just as like java.jdbc) and occasionally database's table name.
For example. you can generate queries with [HoneySql](https://github.com/jkk/honeysql). 

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

(defn update-user [db sql-query]
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

(defn update-user [db table sql-query]
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

Curd's api also has helper methods, which make your crud calls even cleaner than in examples above.

Same code for Create operation can look like this:

```clj
(ns example
  (:require [curd.core :as c]))

(defn save-user [db data]
    (->> data
         (c/prepare-create-map db :users)
         (c/do!)))
    
```

All helper methods are: 
- `prepare-create-map`
- `prepare-query-map`
- `prepare-create-or-update-map`
- `prepare-delete-map`

It is your choice whether you want to use helpers or plain data!

## Customizing

Not satisfied with existing methods? Just add new method to `do!` multimethod using `defcrudmethod` macro and off you go!
Here is example for `::find-one` method:

```clj
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
      (fail :find-one))))
```

Notice also, that namespaced keywords are used as names for crud methods! So you can as well write crudmethod with same name in other namespace, 
and any collisions will be avoided.

## License

Copyright © 2016 [Vincit Oy](https://www.vincit.fi/en/)

Distributed under the Eclipse Public License, the same as Clojure.
