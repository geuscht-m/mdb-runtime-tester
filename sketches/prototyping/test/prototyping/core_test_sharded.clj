(ns prototyping.core-test-sharded
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- start-sharded-cluster
  []
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" "start" "--dir" (str homedir "/tmp/mdb-sharded-cluster"))))

(defn- stop-sharded-cluster
  []
  (let [homedir (System/getenv "HOME")]
    (println (sh "mlaunch" "stop" "--dir" (str homedir "/tmp/mdb-sharded-cluster")))))

(defn wrap-sharded-tests [f]
  (start-sharded-cluster)
  (Thread/sleep 15000)
  (f)
  (stop-sharded-cluster))

(use-fixtures :once wrap-sharded-tests)


(deftest test-get-config-servers-uri
  (testing "Try to retrieve the URIs of the config servers"
    (is (= (get-config-servers-uri "mongodb://localhost:27017") [ "configRepl/localhost:27021" ]))))

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should be a yes"
    (is (is-mongos-process? "mongodb://localhost:27017"))))

(deftest test-is-mongod-process
  (testing "Check various processes to verify that they're mongods or not"
    (is (is-mongod-process? "mongodb://localhost:27018"))
    (not (is-mongod-process? "mongodb://localhost:27017"))))

(deftest test-get-shard-uris
  (testing "Try to retrieve the shard URIs"
    (is (= (get-shard-uris "mongodb://localhost:27017") (list "shard01/localhost:27018" "shard02/localhost:27019" "shard03/localhost:27020")))))

(deftest test-is-sharded
  (testing "Are we connected to a sharded cluster"
    (is (is-sharded-cluster? "mongodb://localhost:27017"))))
