# curd

A Clojure wrapper around [java.jdbc](https://github.com/clojure/java.jdbc) making CRUD operations hassle-free and as simple as Clojure's data structures are.
Curd is heavily customizable and library agnostic. 

## Usage

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

#### Out of the box

Curd has following methods supported out of the box
- :create!
- :find-one
- :find-all
- :update!
- update-or-insert!

Curd's api also has helper methods, that make your crud calls even cleaner than in examples above.

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
- prepare-create-map
- prepare-querymap
- prepare-create-or-update-map

## License

Copyright © 2016 [Vincit Oy](https://www.vincit.fi/en/)

Distributed under the Eclipse Public License, the same as Clojure.
