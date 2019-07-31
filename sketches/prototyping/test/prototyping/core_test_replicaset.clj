(ns prototyping.core-test-replicaset
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- start-test-rs
  []
  (println (sh "/usr/bin/mlaunch" "start" "--dir" "/home/timo/tmp/mdb-test-rs")))

(defn- stop-test-rs
  []
  (println (sh "/usr/bin/mlaunch" "stop" "--dir" "/home/timo/tmp/mdb-test-rs")))

(defn- wrap-rs-tests
  [f]
  (start-test-rs)
  (Thread/sleep 15000) ;; Let the RS stablize
  (f)
  (stop-test-rs))

(use-fixtures :once wrap-rs-tests)

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process"
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

