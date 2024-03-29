(ns tester-core.core-test-sharded
  (:require [clojure.test :refer :all]
            [tester-core.core :refer :all]
            [tester-core.test-helpers :refer :all]
            [taoensso.timbre :as timbre :refer [trace warn debug error]]
            [clojure.java.shell :refer [sh]]))

(defn- control-sharded-cluster
  "Control sharded cluster used for testing via mlaunch. Currently
   only supports start/stop commands"
  [cmd]
  (let [homedir (System/getenv "HOME")]
    (do (sh "mlaunch" cmd "--dir" (str homedir "/tmp/mdb-test/sharded")))))

(defn- wait-test-cluster-ready
  "Waits until the replica sets for the cluster are ready for testing so we don't
   have to play with timeouts all the time"
  []
  (let [shard-1 "mongodb://localhost:27018,localhost:27019,localhost:27020/?replicaSet=shard01"
        shard-2 "mongodb://localhost:27021,localhost:27022,localhost:27023/?replicaSet=shard02"
        shard-3 "mongodb://localhost:27024,localhost:27025,localhost:27026/?replicaSet=shard03"
        retries (atom 0)]
    (while (and (not (and (replicaset-ready? shard-1 3) (replicaset-ready? shard-2 3) (replicaset-ready? shard-3 3))) (< @retries 19))
      (reset! retries (inc @retries))
      (Thread/sleep 500))
    (< @retries 19)))

(defn wrap-sharded-tests
  "Intialisation wrapper for test runner, executed for every test.
   Tries to provide sane environment"
  [f]
  (if (= 0 (num-running-mongo-processes))
    (do
      (control-sharded-cluster "start")
      (Thread/sleep 1500)
      (if (wait-test-cluster-ready)
        (do (is (= 13 (num-running-mongo-processes))) (f))
        (timbre/error "Test sharded cluster readiness timed out"))
      (control-sharded-cluster "stop")
      (wait-mongo-shutdown 40))
    (timbre/error "Inconsistent state - there are mongo processes running that shouldn't be before sharded cluster startup")))

(use-fixtures :each wrap-sharded-tests)
(use-fixtures :once setup-logging-fixture)

(deftest test-get-config-servers-uri
  (testing "Try to retrieve the URIs of the config servers"
    (is (= (get-config-servers-uri "mongodb://localhost:27017") ["configRepl/localhost:27027" "localhost:27028" "localhost:27029"]))))

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should be a yes"
    (is (is-mongos-process? "mongodb://localhost:27017"))
    (is (not (is-mongos-process? "mongodb://localhost:27018")))))

(deftest test-is-mongod-process
  (testing "Check various processes to verify that they're mongods or not"
    (is (is-mongod-process? "mongodb://localhost:27018"))
    (is (not (is-mongod-process? "mongodb://localhost:27017")))))

(deftest test-get-shard-uris
  (testing "Try to retrieve the shard URIs"
    (is (= (get-shard-uris "mongodb://localhost:27017")
           (list "mongodb://localhost:27018,localhost:27019,localhost:27020/?replicaSet=shard01"
                 "mongodb://localhost:27021,localhost:27022,localhost:27023/?replicaSet=shard02"
                 "mongodb://localhost:27024,localhost:27025,localhost:27026/?replicaSet=shard03")))))

(deftest test-is-sharded
  (testing "Are we connected to a sharded cluster"
    (is (is-sharded-cluster? "mongodb://localhost:27017"))))

(deftest test-degraded-single-shard
  (testing "Check that we can create a degraded single shard with the minority of nodes on a single shard disabled"
    (let [shard-uri "mongodb://localhost:27018,localhost:27019,localhost:27020/?replicaSet=shard01"
          restart (make-shard-degraded shard-uri)]
      (Thread/sleep 11000)
      (is (shard-degraded? shard-uri))
      (restart)
      (Thread/sleep 4100)
      (is (not (shard-degraded? shard-uri)))
    )))

(deftest test-degraded-all-shards
  (testing "Check that we can create a sharded cluster with the minority of nodes on all shards disabled"
    (let [cluster-uri "mongodb://localhost:27017"
          ;;shard-list  (get-shard-uris cluster-uri)
          restart     (make-sharded-cluster-degraded cluster-uri)]
      (Thread/sleep 11000)
      (is (not-empty restart))
      (is (cluster-degraded? cluster-uri))
      ;;(println "Degraded restart is " restart)
      (doseq [r restart] (r))
      (Thread/sleep 15000)
      (is (not (cluster-degraded? cluster-uri)))
    )))

(deftest test-read-only-single-shard
  (testing "Check that we turn a single (first) shard on a cluster read only"
    (let [restart   (make-shard-read-only "mongodb://localhost:27018")
          shard-uri "mongodb://localhost:27018,localhost:27019,localhost:27020/?replicaSet=shard01"]
      (Thread/sleep 15000)  ;; Ensure that the replica set has enough time for an election
      (is restart)
      (is (shard-read-only? shard-uri))
      (restart)
      (Thread/sleep 12000)
      (is (not (shard-read-only? shard-uri)))
    )))

(deftest test-read-only-complete-cluster
  (testing "Check that we can turn all shards in a cluster read only"
    (let [cluster-uri "mongodb://localhost:27017"
          shard-list  (get-shard-uris cluster-uri)
          restart     (make-sharded-cluster-read-only cluster-uri)]
      ;;(println "\nHigh level shard list " shard-list)
      ;;(println "Is shard-list a seq " (seq? shard-list) "\n")
      (is (not (nil? restart)))
      (Thread/sleep 15000)
      ;;(println "shards-read-only? first invocation is " (shards-read-only? shard-list))
      (is (shards-read-only? shard-list))
      ;;y(println "Restart is " restart)
      (doseq [r restart] (r))
      (Thread/sleep 32000)
      ;;(println "\nHigh level shard list, again " shard-list)
      ;;(println "shards-read-only? is " (shards-read-only? shard-list))
      (is (not (shards-read-only? shard-list)))
    )))
