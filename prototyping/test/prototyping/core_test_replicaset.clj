(ns prototyping.core-test-replicaset
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [prototyping.sys-helpers :refer :all]
            [prototyping.test-helpers :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- control-test-rs
  "Controls test replica set start/stop using mlaunch"
  [cmd]
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" cmd "--dir" (str homedir "/tmp/mdb-test/replica"))))

(defn- wait-mongod-shutdown
  "Wait until we have no further MongoDB processes running"
  []
  (while (> (num-running-mongo-processes) 0)
    ;;(println "Waiting for test processes to shut down")
    (Thread/sleep 500)))

(defn- wrap-rs-tests
  [f]
  (control-test-rs "start")
  (Thread/sleep 500)
  (if (wait-test-rs-ready "mongodb://localhost:27017,localhost:27018,localhost:27019,localhost:27020,localhost:27021/?replicaSet=replset&connectTimeoutMS=1000" 5 17)
    (f)
    (println "Test replica set not ready in time"))
  (control-test-rs "stop")
  (wait-mongod-shutdown)) 

(use-fixtures :each wrap-rs-tests)

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should fail as we're running mongod"
    (is (not (is-mongos-process? "mongodb://localhost:27017")))))

(deftest test-is-mongod-process
  (testing "Check if we're running against a mongod process"
    (is (is-mongod-process? "mongodb://localhost:27017"))))

(deftest test-is-sharded
  (testing "Are we connected to a sharded cluster"
    (is (not (is-sharded-cluster? "mongodb://localhost:27017")))))

(deftest test-get-rs-topology
  (testing "Check that we retrieve the correct primary and secondaries from the replset status"
    (let [rs-uri      "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset"
          primary      (get (get-rs-primary rs-uri) :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries rs-uri)))]
      ;;(println "Local primary is " primary)
      ;;(println "Local secondaries are " secondaries)
      (is (some? (or (re-matches #"localhost:2701[7-9]" primary) (re-matches #"localhost:2702[1-2]" primary))))
      (is (not (some #{primary} secondaries)))
      (is (= (count secondaries) 4)))))


(deftest test-rs-member-retrieval
  (testing "Make sure we get all the info about the replica set members"
    (is (= (count (get-rs-secondaries "mongodb://localhost:27017")) 4))))

(deftest test-get-random-members
  (testing "Try retrieving a random number of replica set members"
    (is (= (count (get-random-members "mongodb://localhost:27017" 1)) 1))
    (is (= (count (get-random-members "mongodb://localhost:27017" 2)) 2))
    (is (= (sort (map #(get % :name) (get-random-members "mongodb://localhost:27017" 5))) (list "localhost:27017" "localhost:27018" "localhost:27019" "localhost:27020" "localhost:27021")))))

(deftest test-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (let [original-primary (get (get-rs-primary "mongodb://localhost:27017") :name)]
      (trigger-election "mongodb://localhost:27017")
      (Thread/sleep 11000)
      (is (not (= (get (get-rs-primary "mongodb://localhost:27017") :name) original-primary))))))

(deftest test-degraded-rs
  (testing "Check that we can successfully degrade an RS by stopping a minority of nodes"
    (let [rs-uri      "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset"
          restart-cmd (make-rs-degraded "mongodb://localhost:27017") ]
      (is (not (nil? restart-cmd)))
      ;; NOTE: Unfortunately this test is rather timing sensitive at the moment,
      ;;       hence the various sleeps
      (Thread/sleep 12000)
      (is (replicaset-degraded? rs-uri))
      (is (= (num-active-rs-members rs-uri) 3))
      (restart-cmd)
      (Thread/sleep 7000)
      (is (not (replicaset-degraded? rs-uri)))
      (is (= (num-active-rs-members rs-uri) 5)))))

(deftest test-read-only-rs
  (testing "Check that we are able to successfully make a replica set read only
            and restore it afterwards"
    (let [rs-uri      "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset"
          restart-cmd (make-rs-read-only "mongodb://localhost:27017")]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 22000)
      (is (replica-set-read-only? rs-uri))
      (is (= (num-active-rs-members rs-uri) 2))
      (is (replica-set-read-only? rs-uri))
      (restart-cmd)
      (Thread/sleep 12000)
      (is (= (num-active-rs-members rs-uri) 5))
      (is (not (replica-set-read-only? rs-uri)))
      )))

(deftest test-kill-mongo-process
  (testing "Check that we can shut down a mongo process using a signal instead of the
            mongo command"
    (let [cmdline (kill-mongo-process "mongodb://localhost:27017")]
      (is (not (nil? cmdline)))
      (Thread/sleep 12000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 4))
      (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
      (Thread/sleep 7000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 5))
      )))

(deftest test-crash-mongo-process
  (testing "Check that we can successfully 'crash' (kill -9) a mongod and restart it"
    (let [cmdline (kill-mongo-process "mongodb://localhost:27017" :force true)]
      (is (not (nil? cmdline)))
      (Thread/sleep 15000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 4))
      (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
      (Thread/sleep 5000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 5))
      )))

(deftest test-simulate-maintenance
  (testing "Test that the simulate-maintenance function correct does a rolling restart"
    (let [rs-uri "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset"
          num-mongods (num-active-rs-members rs-uri)]
      (simulate-maintenance rs-uri)
      (Thread/sleep 15000)
      (is (= (num-active-rs-members rs-uri) num-mongods)))))
