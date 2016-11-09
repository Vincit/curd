(ns curd.core-test
  (:require [clojure.test :refer :all]
            [curd.core :as c]
            [ragtime.jdbc :as ragtime]
            [ragtime.repl :as repl]
            [clojure.spec.test :as stest]))

(def db "jdbc:postgresql://localhost/curd_test?user=curd_test&password=curd_test")

(def db-driver-manager {:subprotocol "postgresql"
                        :subname "//localhost/curd_test"
                        :user "curd_test"
                        :password "curd_test"})

(def db-with-spec {:some "data"
                   :spec db})

(def config
  {:datastore   (ragtime/sql-database db)
   :migrations  (ragtime/load-resources "migrations")})

(defn up []
  (repl/migrate config))

(defn down []
  (repl/rollback config))

(defn test-fixture [f]
  (up)
  (f)
  (down))

(use-fixtures :each test-fixture)

(def user-data {:username   "janispetka"
                :first-name "Janis"
                :last-name  "Petka"
                :country    "Finland"})

(def user-data-2 {:username   "petkajanis"
                  :first-name "Petka"
                  :last-name  "Janis"
                  :country    "Finland"})

;; ENABLE INSTRUMENTATION
(stest/instrument `c/get-conn)
(stest/instrument `c/insert!)
(stest/instrument `c/do-query)
(stest/instrument `c/execute!)
(stest/instrument `c/delete!)
(stest/instrument `c/find-one-by-id)

(deftest get-conn
  (testing "Invalid input, should throw validation exception"
    (is (thrown? Exception (c/get-conn :invalid))))

  (testing "Valid should return connection"
    (is (= db (c/get-conn db-with-spec)))
    (is (= db (c/get-conn db)))
    (is (= db-driver-manager (c/get-conn db-driver-manager)))))

(deftest create!
  (testing "Invalid db-spec, should throw validation exception"
    (is (thrown? Exception (c/do! {:db        {:invalid "map"}
                                   :method    ::c/create!
                                   :table     :users
                                   :data      user-data}))))

  (testing "Supplied table name is string, should throw validation exception"
    (is (thrown? Exception (c/do! {:db      db
                                   :method  ::c/create!
                                   :table   "users"
                                   :data    user-data}))))

  (testing "create! method returns created row"
    (let [result (c/do! {:db      db
                         :method  ::c/create!
                         :table   :users
                         :data    user-data})]
      (is (= result (assoc user-data :user-id 1)))))

  (testing "create! method returns created row. Using driver manager db config"
    (let [result (c/do! {:db      db-driver-manager
                         :method  ::c/create!
                         :table   :users
                         :data    user-data})]
      (is (= result (assoc user-data :user-id 2)))))

  (testing "create! method returns created row. Using object with spec"
    (let [result (c/do! {:db      db-with-spec
                         :method  ::c/create!
                         :table   :users
                         :data    user-data})]
      (is (= result (assoc user-data :user-id 3)))))

  (testing "data has not existing column, should throw exception"
    (is (thrown-with-msg? Exception #":curd.core/create! crud method failed"
                          (c/do! {:db      db
                                  :method  ::c/create!
                                  :table   :users
                                  :data    (assoc user-data :not-existing "123")}))))

  (testing "saving multiple rows, should return rows"
    (let [result (c/do! {:db      db
                         :method  ::c/create!
                         :table   :users
                         :data    [user-data user-data-2]})]
      (is (seq? result))
      (is (= (count result) 2))
      (is (= ["janispetka" "petkajanis"] (reduce #(conj %1 (:username %2)) [] result)))))

  (testing "saving multiple rows, should return rows. Using object with spec"
    (let [result (c/do! {:db      db-with-spec
                         :method  ::c/create!
                         :table   :users
                         :data    [user-data user-data-2]})]
      (is (seq? result))
      (is (= (count result) 2))
      (is (= ["janispetka" "petkajanis"] (reduce #(conj %1 (:username %2)) [] result))))))

(deftest create!-with-transaction
  (testing "saving multiple rows, second row has invalid data, transaction should be aborted and rows should be saved"
    (is (thrown? Exception (c/do! {:db      db
                                   :method  ::c/create!
                                   :table   :users
                                   :data    [user-data (assoc user-data-2 :invalid-field 2)]})))
    (is (= (-> (c/do! {:db      db
                       :method  ::c/find-all
                       :query   ["SELECT * from users"]})
               (count)) 0))))

(deftest find-one
  (testing "Invalid db-spec, should throw validation exception"
    (is (thrown? Exception (c/do! {:db      {:invalid "map"}
                                   :method  ::c/find-one
                                   :query   ["SELECT * from users WHERE username = ?" "janispetka"]}))))
  (testing "Should return found row. Using all kinds of db objects."
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    user-data})
    (is (=  (c/do! {:db      db
                    :method  ::c/find-one
                    :query   ["SELECT * from users WHERE username = ?" "janispetka"]})
            (assoc user-data :user-id 1)))
    (is (= (c/do! {:db      db-driver-manager
                   :method  ::c/find-one
                   :query   ["SELECT * from users WHERE username = ?" "janispetka"]})
           (assoc user-data :user-id 1)))
    (is (= (c/do! {:db      db-with-spec
                   :method  ::c/find-one
                   :query   ["SELECT * from users WHERE username = ?" "janispetka"]})
           (assoc user-data :user-id 1))))

  (testing "Query by username and id, should return found row. Using all kinds of db objects"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    user-data})
    (is (= (c/do! {:db      db
                   :method  ::c/find-one
                   :query   ["SELECT * from users WHERE username = ? and user_id = ?" "janispetka" 1]})
           (assoc user-data :user-id 1)))
    (is (= (c/do! {:db      db-driver-manager
                   :method  ::c/find-one
                   :query   ["SELECT * from users WHERE username = ? and user_id = ?" "janispetka" 1]})
           (assoc user-data :user-id 1)))
    (is (= (c/do! {:db      db-with-spec
                   :method  ::c/find-one
                   :query   ["SELECT * from users WHERE username = ? and user_id = ?" "janispetka" 1]})
           (assoc user-data :user-id 1)))))

(deftest find-one-with-row-fn
  (testing "Should find row, apply row-fn and return it"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    user-data})
    (is (= (c/do! {:db      db
                   :method  ::c/find-one
                   :query   ["SELECT * from users WHERE username = ? and user_id = ?" "janispetka" 1]
                   :row-fn  #(assoc % :active true)})
           (-> user-data
               (assoc :user-id 1)
               (assoc :active true))))))

(deftest find-one-by-id
  (testing "Should find row and return it"
    (let [user-data-with-id (assoc user-data :user-id 20)]
      (c/do! {:db      db
              :method  ::c/create!
              :table   :users
              :data    user-data-with-id})
      (is (= (c/do! {:method     ::c/find-one-by-id
                     :db         db
                     :table      :users
                     :key-value  20
                     :key-name   :user-id}) user-data-with-id))
      (is (= (c/do! {:method     ::c/find-one-by-id
                     :db         db-driver-manager
                     :table      :users
                     :key-value  20
                     :key-name   :user-id}) user-data-with-id))
      (is (= (c/do! {:method     ::c/find-one-by-id
                     :db         db-with-spec
                     :table      :users
                     :key-value  20
                     :key-name   :user-id}) user-data-with-id)))))

(deftest find-all
  (testing "Should return two rows"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    [user-data user-data-2]})
    (is (= (c/do! {:db      db
                   :method  ::c/find-all
                   :query   ["SELECT * from users"]})
           (vector (assoc user-data :user-id 1) (assoc user-data-2 :user-id 2))))
    (is (= (c/do! {:db      db-driver-manager
                   :method  ::c/find-all
                   :query   ["SELECT * from users"]}) (vector (assoc user-data :user-id 1) (assoc user-data-2 :user-id 2))))
    (is (= (c/do! {:db      db-with-spec
                   :method  ::c/find-all
                   :query   ["SELECT * from users"]})
           (vector (assoc user-data :user-id 1) (assoc user-data-2 :user-id 2))))))

(deftest find-all-with-result-set-fn
  (testing "Should return only first row according to result-set-fn"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    [user-data user-data-2]})
    (is (= (c/do! {:method        ::c/find-all
                   :db            db
                   :query         ["SELECT * from users"]
                   :result-set-fn first}) (assoc user-data :user-id 1)))
    (is (= (c/do! {:method        ::c/find-all
                   :db            db-driver-manager
                   :query         ["SELECT * from users"]
                   :result-set-fn first}) (assoc user-data :user-id 1)))
    (is (= (c/do! {:method        ::c/find-all
                   :db            db-with-spec
                   :query         ["SELECT * from users"]
                   :result-set-fn first}) (assoc user-data :user-id 1)))))

(deftest find-all-with-row-fn
  (testing "Should return all rows without :user-id according to row-fn"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    [user-data user-data-2]})
    (is (= (c/do! {:method  ::c/find-all
                   :db      db
                   :query   ["SELECT * from users"]
                   :row-fn  #(dissoc %1 :user-id)}) (vector user-data user-data-2)))
    (is (= (c/do! {:method  ::c/find-all
                   :db      db-driver-manager
                   :query   ["SELECT * from users"]
                   :row-fn  #(dissoc %1 :user-id)}) (vector user-data user-data-2)))
    (is (= (c/do! {:method  ::c/find-all
                   :db      db-with-spec
                   :query   ["SELECT * from users"]
                   :row-fn  #(dissoc %1 :user-id)}) (vector user-data user-data-2)))))

(deftest update!
  (testing "Should update row"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    user-data})
    (is (=  (c/do! {:db      db
                    :method  ::c/update!
                    :query   ["UPDATE users SET country = ? where user_id = ?" "Sweden" 1]})
            [1]))
    (is (= (c/do! {:db      db-driver-manager
                   :method  ::c/update!
                   :query   ["UPDATE users SET country = ? where user_id = ?" "Finland" 1]}) [1]))
    (is (= (c/do! {:db      db-with-spec
                   :method  ::c/update!
                   :query   ["UPDATE users SET country = ? where user_id = ?" "Sweden" 1]}) [1]))))

(deftest delete!
  (testing "Should delete row"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    user-data})
    (is (= (c/do! {:db      db
                   :method  ::c/delete!
                   :table   :users
                   :query   ["user_id = ?" 1]})
           [1]))
    (is (empty? (c/do! {:db      db
                        :method  ::c/find-all
                        :query   ["SELECT * from users"]}))))
  (testing "Should delete row, using db driver manager"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    user-data})
    (is (= (c/do! {:db      db-driver-manager
                   :method  ::c/delete!
                   :table   :users
                   :query   ["user_id = ?" 2]})
           [1]))
    (is (empty? (c/do! {:db      db
                        :method  ::c/find-all
                        :query   ["SELECT * from users"]}))))
  (testing "Should delete row, using object with db spec"
    (c/do! {:db      db
            :method  ::c/create!
            :table   :users
            :data    user-data})
    (is (= (c/do! {:db      db-with-spec
                   :method  ::c/delete!
                   :table   :users
                   :query   ["user_id = ?" 3]}) [1]))
    (is (empty? (c/do! {:db      db
                        :method  ::c/find-all
                        :query   ["SELECT * from users"]})))))

(deftest update-or-insert!
  (testing "Should create new row"
    (let [result (c/do! {:method   ::c/update-or-insert!
                         :table :users
                         :db    db
                         :query ["SELECT * from users WHERE username = ?" "janispetka"]
                         :data  user-data})]
      (is (= result (assoc user-data :user-id 1)))))

  (testing "Already exists, should update"
    (let [updated-user-data (merge user-data {:user-id 1 :last-name "Petkovic"})
          result (c/do! {:method   ::c/update-or-insert!
                         :table :users
                         :db    db
                         :query ["SELECT * from users WHERE username = ?" "janispetka"]
                         :data  updated-user-data})]
      (is (= result updated-user-data)))))