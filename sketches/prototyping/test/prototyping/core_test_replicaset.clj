(ns prototyping.core-test-replicaset
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [prototyping.sys-helpers :refer :all]
            [prototyping.test-helpers :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- start-test-rs
  []
  ;;(println (System/getenv "PATH"))
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" "start" "--dir" (str homedir "/tmp/mdb-test-rs"))))

(defn- stop-test-rs
  []
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" "stop" "--dir" (str homedir "/tmp/mdb-test-rs"))))

(defn- wrap-rs-tests
  [f]
  (start-test-rs)
  (Thread/sleep 15000) ;; Let the RS stablize
  (f)
  (stop-test-rs))

(use-fixtures :each wrap-rs-tests)

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should fail as we're running mongod"
    (not (is-mongos-process? "mongodb://localhost:27017"))))

(deftest test-is-mongod-process
  (testing "Check if we're running against a mongod process"
    (is (is-mongod-process? "mongodb://localhost:27017"))))

(deftest test-is-sharded
  (testing "Are we connected to a sharded cluster"
    (not (is-sharded-cluster? "mongodb://localhost:27017"))))

(deftest test-get-rs-topology
  (testing "Check that we retrieve the correct primary and secondaries from the replset status"
    (let [primary      (get (get-rs-primary "mongodb://localhost:27017") :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries "mongodb://localhost:27017")))]
      (not (nil? (re-matches #"mongodb://localhost:2701[7-9]" primary)))
      (not (some #{primary} secondaries))
      (is  (= (count secondaries) 2)))))


(deftest test-rs-member-retrieval
  (testing "Make sure we get all the info about the replica set members"
    (is (= (count (get-rs-secondaries "mongodb://localhost:27017")) 2))))

(deftest test-get-random-members
  (testing "Try retrieving a random number of replica set members"
    (is (= (count (get-random-members "mongodb://localhost:27017" 1)) 1))
    (is (= (count (get-random-members "mongodb://localhost:27017" 2)) 2))
    (is (= (sort (map #(get % :name) (get-random-members "mongodb://localhost:27017" 3))) (list "localhost:27017" "localhost:27018" "localhost:27019")))))

(deftest test-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (let [original-primary (get (get-rs-primary "mongodb://localhost:27017") :name)]
      (trigger-election "mongodb://localhost:27017")
      (Thread/sleep 10000)
      (not (= (get (get-rs-primary "mongodb://localhost:27017") :name) original-primary)))))

(deftest test-degraded-rs
  (testing "Check that we can successfully degrade an RS by stopping one of the members"
    (let [restart-cmd (make-rs-degraded "mongodb://localhost:27017") ]
      (not (nil? restart-cmd))
      ;; NOTE: Unfortunately this test is rather timing sensitive at the moment,
      ;;       hence the various sleeps
      (Thread/sleep 30000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 2))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 3)))))

(deftest test-read-only-rs
  (testing "Check that we are able to successfully make a replica set read only
            and restore it afterwards"
    (let [restart-cmd (make-rs-read-only "mongodb://localhost:27017")]
      (not (nil? restart-cmd))
      (Thread/sleep 15000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 1))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 3))
      )))
