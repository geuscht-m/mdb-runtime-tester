(ns prototyping.core-test-sharded
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [prototyping.test-helpers :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- start-sharded-cluster
  []
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" "start" "--dir" (str homedir "/tmp/mdb-test/sharded"))))

(defn- stop-sharded-cluster
  []
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" "stop" "--dir" (str homedir "/tmp/mdb-test/sharded"))))

(defn wrap-sharded-tests [f]
  (start-sharded-cluster)
  (Thread/sleep 15000)
  (f)
  (stop-sharded-cluster))

(use-fixtures :each wrap-sharded-tests)


(deftest test-get-config-servers-uri
  (testing "Try to retrieve the URIs of the config servers"
    (is (= (get-config-servers-uri "mongodb://localhost:27017") ["configRepl/localhost:27027" "localhost:27028" "localhost:27029"]))))

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should be a yes"
    (is (is-mongos-process? "mongodb://localhost:27017"))
    (not (is-mongos-process? "mongodb://localhost:27018"))))

(deftest test-is-mongod-process
  (testing "Check various processes to verify that they're mongods or not"
    (is (is-mongod-process? "mongodb://localhost:27018"))
    (not (is-mongod-process? "mongodb://localhost:27017"))))

(deftest test-get-shard-uris
  (testing "Try to retrieve the shard URIs"
    ;;(println (get-shard-uris "mongodb://localhost:27017"))
    (is (= (get-shard-uris "mongodb://localhost:27017")
           (list "shard01/localhost:27018,localhost:27019,localhost:27020" "shard02/localhost:27021,localhost:27022,localhost:27023" "shard03/localhost:27024,localhost:27025,localhost:27026")))))

(deftest test-is-sharded
  (testing "Are we connected to a sharded cluster"
    (is (is-sharded-cluster? "mongodb://localhost:27017"))))

(deftest test-degraded-single-shard
  (testing "Check that we can create a degraded single shard with the minority of nodes on a single shard disabled"
    ))

(deftest test-degraded-all-shards
  (testing "Check that we can create a sharded cluster with the minority of nodes on all shards disabled"
    ))

(deftest test-read-only-single-shard
  (testing "Check that we turn a single (first) shard on a cluster read only"
    (let [restart  (make-shard-read-only "" "mongodb://localhost:27018")]
      (Thread/sleep 15000)  ;; Ensure that the replica set has enough time for an election
      (is (shard-read-only? "mongodb://shard01/localhost:27018,localhost:27019,localhost:27020"))
      (restart)
      (Thread/sleep 10000)
      (not (shard-read-only? "mongodb://shard01/localhost:27018,localhost:27019,localhost:27020"))
    )))

(deftest test-read-only-complete-cluster
  (testing "Check that we can turn all shards in a cluster read only"
    (let [restart    (make-sharded-cluster-read-only "mongodb://localhost:27017")
          shard-list (get-shard-uris "mongodb://localhost:27017")]
      (println "\n" shard-list)
      (Thread/sleep 11000)
      (is (shards-read-only? shard-list))
      (doall restart)
      (Thread/sleep 10000)
      (not (shards-read-only? shard-list))
    )))
