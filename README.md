# curd

A Clojure wrapper around [java.jdbc](https://github.com/clojure/java.jdbc) making CRUD operations hassle-free and as simple as Clojure's data structures are.
Curd is heavily customizable and library agnostic.
Usually we use the same set of CRUD methods all over again and again. Life would so much easier, if we could have a single 
interface to perform all our queries, inserts and so on. That's exactly what Curd offers.

## Usage

Curd doesn't know anything about underlying database. It doesn't care about the way you generate queries either. All it needs
is database's spec, sql with parameters (just as like java.jdbc) and occasionally database's table name.
For example. you can generate queries with [HoneySql](https://github.com/jkk/honeysql). 



### Creating

```clj
(ns example
  (:require [curd.core :as c]))

(defn save-user [db data]
    (c/do! {:method   :create!
            :db       db
            :table    :users
            :data     data}))
    
```

### Reading

```clj
(ns example
  (:require [curd.core :as c]))

(defn update-user [db sql-query]
    (c/do! {:method   :find-all
            :db       db
            :query    sql-query}))
    
```

### Updating

```clj
(ns example
  (:require [curd.core :as c]))

(defn update-user [db sql-query]
    (c/do! {:method   :update!
            :db       db
            :query    sql-query}))
    
```

### Deleting

```clj
(ns example
  (:require [curd.core :as c]))

(defn update-user [db table sql-query]
    (c/do! {:method   :delete!
            :db       db
            :table    table
            :query    sql-query}))
    
```

#### Out of the box

Curd has following methods supported out of the box: 
- `:create!`
- `:find-one`
- `:find-all`
- `:update!`
- `:update-or-insert!`

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
Here is example for `:update-or-insert!` method:

```clj
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
```

## License

Copyright © 2016 [Vincit Oy](https://www.vincit.fi/en/)

Distributed under the Eclipse Public License, the same as Clojure.
