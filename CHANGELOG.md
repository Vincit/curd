# CURD Changelog

# 0.1.3-SNAPSHOT

### Bug fixes
* add missing dev profile
* wrap print-sql-exception function

### Features

* add validations with spec
* add support for namespaced crud methods
* improve testing workflow
* improve transaction support

### Breaking changes

Requires **1.9.0-alpha8** version of clojure.

Namespaced keywords are used now instead of simple keywords as crud method's names. 

You need to rename existing calls to curd's core crudmethods to reference a core namespace, e.g:

**Before:** 

```clj
(ns example
  (:require [curd.core :as c]))

(defn update-user [db sql-query]
    (c/do! {:method   :find-all
            :db       db
            :query    sql-query}))
    
```

**After:** 

```clj
(ns example
  (:require [curd.core :as c]))

(defn update-user [db sql-query]
    (c/do! {:method   ::c/find-all
            :db       db
            :query    sql-query}))
    
```