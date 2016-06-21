(ns curd.core-test
  (:require [clojure.test :refer :all]
            [curd.core :as curd]
            [ragtime.jdbc :as ragtime]
            [ragtime.repl :as repl]))

(def db "jdbc:postgresql://localhost/curd_test?user=curd_test&password=curd_test")

(def db-driver-manager {:subprotocol "postgresql"
                        :subname "//localhost/curd_test"
                        :user "curd_test"
                        :password "curd_test"})

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

(deftest create!
  (testing "Invalid db-spec, should throw validation exception"
    (is (thrown? Exception (->> user-data
                                (curd/prepare-create-map {:invalid "map"} :users)
                                (curd/do!)))))

  (testing "Supplied table name is string, should throw validation exception"
    (is (thrown? Exception (->> user-data
                                (curd/prepare-create-map db "users")
                                (curd/do!)))))

  (testing "create! method returns created row"
    (let [result (->> user-data
                      (curd/prepare-create-map db :users)
                      (curd/do!))]
      (is (= result (assoc user-data :user-id 1)))))

  (testing "create! method returns created row. Using driver manager db config"
    (let [result (->> user-data
                      (curd/prepare-create-map db-driver-manager :users)
                      (curd/do!))]
      (is (= result (assoc user-data :user-id 2)))))

  (testing "data has not existing column, should throw exception"
    (is (thrown-with-msg? Exception #":create! crud method failed. Check the SQL Exception description above"
                          (->> (assoc user-data :not-existing "123")
                               (curd/prepare-create-map db :users)
                               (curd/do!)))))

  (testing "saving multiple rows, should return rows"
    (let [result (->> [user-data user-data-2]
                      (curd/prepare-create-map db :users)
                      (curd/do!))]
      (is (seq? result))
      (is (= (count result) 2))
      (is (= ["janispetka" "petkajanis"] (reduce #(conj %1 (:username %2)) [] result))))))

(deftest find-one
  (testing "Invalid db-spec, should throw validation exception"
    (is (thrown? Exception (->> ["SELECT * from users WHERE username = ?" "janispetka"]
                                (curd/prepare-query-map {:invalid "map"} :find-one)
                                (curd/do!)))))
  (testing "Should return found row"
    (do
      (->> user-data
           (curd/prepare-create-map db :users)
           (curd/do!))
      (is (= (->> ["SELECT * from users WHERE username = ?" "janispetka"]
                  (curd/prepare-query-map db :find-one)
                  (curd/do!)) (assoc user-data :user-id 1)))))
  
  (testing "Query by username and id, should return found row"
    (do
      (->> user-data
           (curd/prepare-create-map db :users)
           (curd/do!))
      (is (= (->> ["SELECT * from users WHERE username = ? and user_id = ?" "janispetka" 1]
                  (curd/prepare-query-map db :find-one)
                  (curd/do!)) (assoc user-data :user-id 1))))))

(deftest find-one-by-id
  (testing "Should find row and return it"
    (let [user-data-with-id (assoc user-data :user-id 20)]
      (do (->> user-data-with-id
               (curd/prepare-create-map db :users)
               (curd/do!))
          (is (= (-> {:method     :find-one-by-id
                      :db         db
                      :table      :users
                      :key-value  20
                      :key-name   :user-id}
                     (curd/do!)) user-data-with-id))))))

(deftest find-all
  (testing "Should return two rows"
    (do
      (doall (map #(->> %1
                        (curd/prepare-create-map db :users)
                        (curd/do!)) (vector user-data user-data-2)))
      (is (= (->> ["SELECT * from users"]
                  (curd/prepare-query-map db :find-all)
                  (curd/do!)) (vector (assoc user-data :user-id 1) (assoc user-data-2 :user-id 2)))))))

(deftest find-all-with-result-set-fn
  (testing "Should return only first row according to result-set-fn"
    (do
      (doall (map #(->> %1
                        (curd/prepare-create-map db :users)
                        (curd/do!)) (vector user-data user-data-2)))
      (is (= (->> {:method        :find-all
                   :db            db
                   :query         ["SELECT * from users"]
                   :result-set-fn first}
                  (curd/do!)) (assoc user-data :user-id 1))))))

(deftest find-all-with-row-fn
  (testing "Should return all rows without :user-id according to row-fn"
    (do
      (doall (map #(->> %1
                        (curd/prepare-create-map db :users)
                        (curd/do!)) (vector user-data user-data-2)))
      (is (= (->> {:method  :find-all
                   :db      db
                   :query   ["SELECT * from users"]
                   :row-fn  #(dissoc %1 :user-id)}
                  (curd/do!)) (vector user-data user-data-2))))))

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
