(ns prototyping.core-test-replicaset
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- start-test-rs
  []
  (println (System/getenv "PATH"))
  (let [homedir (System/getenv "HOME")]
    (println (sh "mlaunch" "start" "--dir" (str homedir "/tmp/mdb-test-rs")))))

(defn- stop-test-rs
  []
  (let [homedir (System/getenv "HOME")]
        (println (sh "mlaunch" "stop" "--dir" (str homedir "/tmp/mdb-test-rs")))))

(defn- wrap-rs-tests
  [f]
  (start-test-rs)
  (Thread/sleep 15000) ;; Let the RS stablize
  (f)
  (stop-test-rs))

(use-fixtures :once wrap-rs-tests)

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
  (testing "Check that we retrieve the correct primary from the replset status"
    (is (= (get (get-rs-primary "mongodb://localhost:27017") :name) "localhost:27017"))
    (is (= (sort (map #(get % :name) (get-rs-secondaries "mongodb://localhost:27017"))) (sort (list "localhost:27018" "localhost:27019"))))))

(deftest test-rs-member-retrieval
  (testing "Make sure we get all the info about the replica set members"
    (is (= (count (get-rs-secondaries "mongodb://localhost:27017")) 2))))

(deftest test-get-random-members
  (testing "Try retrieving a random number of replica set members"
    (is (= (count (get-random-members "mongodb://localhost:27017" 1)) 1))
    (is (= (count (get-random-members "mongodb://localhost:27017" 2)) 2))
    (is (= (sort (map #(get % :name) (get-random-members "mongodb://localhost:27017" 3))) (list "localhost:27017" "localhost:27018" "localhost:27019")))))
