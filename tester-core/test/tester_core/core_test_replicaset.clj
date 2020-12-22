(ns tester-core.core-test-replicaset
  (:require [clojure.test :refer :all]
            [tester-core.core :refer :all]
            [tester-core.sys-helpers :refer :all]
            [tester-core.test-helpers :refer :all]
            [tester-core.mini-driver :as md :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- control-test-rs
  "Controls test replica set start/stop using mlaunch"
  [cmd]
  (let [homedir (System/getenv "HOME")]
    (do (sh "mlaunch" cmd "--dir" (str homedir "/tmp/mdb-test/replica")))))

(defn- wrap-rs-tests
  "Intialisation wrapper for test runner, executed for every test.
   Tries to provide sane environment"
  [f]
  (is (= 0 (num-running-mongo-processes)))
  (control-test-rs "start")
  (Thread/sleep 1500)
  (if (wait-test-rs-ready "mongodb://localhost:27017,localhost:27018,localhost:27019,localhost:27020,localhost:27021/?replicaSet=replset&connectTimeoutMS=1000" 5 19)
    (f)
    (println "Test replica set not ready in time"))
  (control-test-rs "stop")
  (wait-mongo-shutdown 20)
  (is (= 0 (num-running-mongo-processes))))

(use-fixtures :each wrap-rs-tests)
(use-fixtures :once setup-logging-fixture)

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
    (let [rs-uri       "mongodb://localhost:27017,localhost:27018,localhost:27019,localhost:27020,localhost:27021/?replicaSet=replset"
          conn         (md/mdb-connect rs-uri)
          primary      (get (get-rs-primary conn) :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries conn)))]
      ;;(println "Local primary is " primary)
      ;;(timbre/debug "Topology - secondaries are " secondaries)
      (is (= 5 (num-active-rs-members conn)))
      (md/mdb-disconnect conn)
      (is (some? (or (re-matches #"localhost:2701[7-9]" primary) (re-matches #"localhost:2702[1-2]" primary))))
      (is (not (some #{primary} secondaries)))
      ;;(timbre/debug "Secondaries are " secondaries)
      (is (= 4 (count secondaries))))))


(deftest test-rs-member-retrieval
  (testing "Make sure we get all the info about the replica set members"
    (is (= 4 (count (get-rs-secondaries "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset"))))))

(deftest test-get-random-members
  (testing "Try retrieving a random number of replica set members"
    (is (= 1 (count (get-random-members "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset" 1))))
    (is (= 2 (count (get-random-members "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset" 2))))
    (is (= (sort (map #(get % :name) (get-random-members "mongodb://localhost:27017" 5))) (list "localhost:27017" "localhost:27018" "localhost:27019" "localhost:27020" "localhost:27021")))))

(deftest test-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (let [rs-uri           "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset"
          original-primary (get (get-rs-primary rs-uri) :name)]
      (trigger-election rs-uri)
      (Thread/sleep 11000)
      (is (not (= (get (get-rs-primary rs-uri) :name) original-primary))))))

(deftest test-degraded-rs
  (testing "Check that we can successfully degrade an RS by stopping a minority of nodes"
    (let [rs-uri      "mongodb://localhost:27017,localhost:27018,localhost:27019,localhost:27020,localhost:27021/?replicaSet=replset"
          restart-cmd (make-rs-degraded rs-uri) ]
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
    (let [rs-uri      "mongodb://localhost:27017,localhost:27018,localhost:27019,localhost:27020,localhost:27021/?replicaSet=replset"
          restart-cmd (make-rs-read-only rs-uri)]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 22000)
      (is (replica-set-read-only? rs-uri))
      (is (= 2 (num-active-rs-members rs-uri)))
      (is (replica-set-read-only? rs-uri))
      (restart-cmd)
      (Thread/sleep 12000)
      (is (= 5 (num-active-rs-members rs-uri)))
      (is (not (replica-set-read-only? rs-uri)))
      )))

(deftest test-kill-mongo-process
  (testing "Check that we can shut down a mongo process using a signal instead of the
            mongo command"
    (let [rs-uri  "mongodb://localhost:27017,localhost:27018,localhost:27019/?replicaSet=replset"
          cmdline (kill-mongo-process "mongodb://localhost:27017")]
      (is (not (nil? cmdline)))
      (Thread/sleep 12000)
      (is (= (num-active-rs-members rs-uri) 4))
      (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
      (Thread/sleep 7000)
      (is (= (num-active-rs-members rs-uri) 5))
      )))

(deftest test-crash-mongo-process
  (testing "Check that we can successfully 'crash' (kill -9) a mongod and restart it"
    (let [rs-uri  "mongodb://localhost:27017,localhost:27018,localhost:27019,localhost:27020,localhost:27021/?replicaSet=replset"
          cmdline (kill-mongo-process "mongodb://localhost:27017" :force true)]
      (is (not (nil? cmdline)))
      (Thread/sleep 15000)
      (is (= 4 (num-active-rs-members rs-uri)))
      (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
      (Thread/sleep 5000)
      (is (= 5 (num-active-rs-members rs-uri)))
      )))

(deftest test-simulate-maintenance
  (testing "Test that the simulate-maintenance function correct does a rolling restart"
    (let [rs-uri "mongodb://localhost:27017,localhost:27018,localhost:27019,localhost:27020,localhost:27021/?replicaSet=replset"
          num-mongods (num-active-rs-members rs-uri)]
      (is (= 5 num-mongods))
      (simulate-maintenance rs-uri)
      (Thread/sleep 15000)
      (is (= num-mongods (num-active-rs-members rs-uri))))))
