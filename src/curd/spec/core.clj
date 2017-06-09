(ns curd.spec.core
  (:require [clojure.spec.alpha :as s])
  (:import (clojure.lang IFn MultiFn)
           (javax.sql DataSource)
           (javax.naming Name)
           (java.sql Connection)))


(defn function? [x]
  (instance? IFn x))

(defn multi-fn? [x]
  instance? MultiFn x)

;; db-spec Factory
(s/def ::factory function?)
(s/def ::factory-object
  (s/keys :req-un [::factory]))

;; db-spec Connection
(defn connection? [x]
  (instance? Connection x))
(s/def ::connection connection?)
(s/def ::connection-object
  (s/keys :req-un [::connection]))

;; db-spec Driver Manager
(s/def ::subprotocol string?)
(s/def ::subname string?)
(s/def ::classname string?)
(s/def ::driver-manager
  (s/keys :req-un [::subprotocol ::subname]
          :opt-un [::classname]))

;; db-spec Driver Manager (alternative)
(s/def ::dbtype string?)
(s/def ::dbname string?)
(s/def ::host string?)
(s/def ::port number?)
(s/def ::driver-manager-alt
  (s/keys :req-un [::dbtype ::dbname ::host ::port]))

;; db-spec Datasource
(defn datasource? [x]
  (instance? DataSource x))
(s/def ::datasource datasource?)
(s/def ::username string?)
(s/def ::user string?)
(s/def ::password string?)
(s/def ::data-source
  (s/keys :req-un [::datasource]
          :opt-un [::username ::user ::password]))

;; db-spec JNDI
(defn javax-name? [x]
  (instance? Name x))
(s/def ::name (s/or :string string?
                    :javax-name javax-name?))
(s/def ::environment map?)
(s/def ::jndi
  (s/keys :req-un [::name]
          :opt-un [::environment]))

;; db-spec Raw
(s/def ::connection-uri string?)
(s/def ::raw
  (s/keys :req-un [::connection-uri]))

;; db-spec URI
(s/def ::uri uri?)

;;db-spec String
(s/def ::string string?)

(s/def ::spec (s/keys ::req-un [::conn]))

(s/def ::conn-or-spec (s/or :spec ::spec
                            :conn ::conn))

(s/def ::conn (s/or
                ;; Connection
                :connection-object ::connection-object
                ;; Factory
                :factory-object ::factory-object
                ;; Driver Manager
                :driver-manager ::driver-manager
                ;; Alternative Driver Manager
                :driver-manager-alt ::driver-manager-alt
                ;; Datasource
                :data-source ::data-source
                ;; JNDI
                :jndi ::jndi
                ;; Raw
                :raw ::raw
                ;; URI
                :uri ::uri
                :string ::string
                ))
(s/def ::string-or-number (s/or :string string?
                                :number number?))
(s/def ::query (s/cat :sql string?
                      :params (s/* ::string-or-number)))
(s/def ::result-set-fn function?)
(s/def ::row-fn function?)
(s/def ::entities-fn function?)
(s/def ::identifiers-fn function?)

(s/def ::table keyword?)
(s/def ::data (s/or :map map?
                    :vector vector?
                    :seq seq?))

(s/def ::key-value ::string-or-number)
(s/def ::key-name keyword?)

;; -------------- PUBLIC SPECS -----------------

(s/def ::insert!-args
  (s/keys :req-un [::conn-or-spec ::table ::data]
          :opt-un [::entities-fn]))

(s/def ::do-query-args
  (s/keys :req-un [::conn-or-spec ::query]
          :opt-un [::result-set-fn ::row-fn]))

(s/def ::execute!-args
  (s/keys :req-un [::conn-or-spec ::query]))

(s/def ::delete!-args
  (s/keys :req-un [::conn-or-spec ::table ::query]))

(s/def ::find-one-by-id-args
  (s/keys ::req-un [::conn-or-spec ::table ::key-value]
          ::opt-un [::key-name ::result-set-fn ::entities-fn ::identifiers-fn]))

(s/def ::multi-fn multi-fn?)