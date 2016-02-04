(ns curd.core-test
  (:require [clojure.test :refer :all]
            [curd.core :as curd]
            [ragtime.jdbc :as ragtime]
            [ragtime.repl :as repl]))

(def db "jdbc:postgresql://localhost/curd_test?user=curd_test&password=curd_test")

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

(deftest create!
  (testing "create! method returns created row"
    (let [result (->> user-data
                      (curd/prepare-create-map db :users)
                      (curd/do!))]
      (is (= result (assoc user-data :user-id 1)))))

  (testing "data has not existing column, should throw exception"
    (is (thrown-with-msg? Exception #":create! crud method failed. Check the SQL Exception description above"
                          (->> (assoc user-data :not-existing "123")
                               (curd/prepare-create-map db :users)
                               (curd/do!))))))

(deftest find-one
  (testing "Should return found row"
    (do
      (->> user-data
           (curd/prepare-create-map db :users)
           (curd/do!))
      (is (= (->> ["SELECT * from users WHERE username = ?" "janispetka"]
                  (curd/prepare-query-map db :find-one)
                  (curd/do!)) (assoc user-data :user-id 1))))))

(deftest update!
  (testing "Should update row"
    (do
      (->> user-data
           (curd/prepare-create-map db :users)
           (curd/do!))
      (is (= (->> ["UPDATE users SET country = ? where user_id = ?" "Sweden" 1]
                  (curd/prepare-query-map db :update!)
                  (curd/do!)) [1])))))

(deftest delete!
  (testing "Should delete row"
    (do
      (->> user-data
           (curd/prepare-create-map db :users)
           (curd/do!)))
    (is (= (->> ["user_id = ?" 1]
                (curd/prepare-delete-map db :delete! :users)
                (curd/do!)) [1]))
    (is (empty? (->> ["SELECT * from users"]
                (curd/prepare-query-map db :find-all)
                (curd/do!))))))

