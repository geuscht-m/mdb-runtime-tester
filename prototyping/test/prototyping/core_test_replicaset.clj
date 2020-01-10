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
    (sh "mlaunch" "start" "--dir" (str homedir "/tmp/mdb-test/replica"))))

(defn- stop-test-rs
  []
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" "stop" "--dir" (str homedir "/tmp/mdb-test/replica"))))

(defn- wrap-rs-tests
  [f]
  (start-test-rs)
  (Thread/sleep 10000) ;; Let the RS stablize
  (f)
  (stop-test-rs))

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
    (let [primary      (get (get-rs-primary "mongodb://localhost:27017") :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries "mongodb://localhost:27017")))]
      (println primary)
      (println secondaries)
      (not (nil? (re-matches #"mongodb://localhost:2701[7-9]" primary)))
      (not (some #{primary} secondaries))
      (is  (= (count secondaries) 4)))))


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
    (let [restart-cmd (make-rs-degraded "mongodb://localhost:27017") ]
      (not (nil? restart-cmd))
      ;; NOTE: Unfortunately this test is rather timing sensitive at the moment,
      ;;       hence the various sleeps
      (Thread/sleep 30000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 3))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 5)))))

(deftest test-read-only-rs
  (testing "Check that we are able to successfully make a replica set read only
            and restore it afterwards"
    (let [restart-cmd (make-rs-read-only "mongodb://localhost:27017")]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 20000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 2))
      (is (replica-set-read-only? (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members (str "mongodb://localhost:" (first (mongodb-port-list (available-mongods))))) 5))
      )))

(deftest test-kill-mongo-process
  (testing "Check that we can shut down a mongo process using a signal instead of the
            mongo command"
    (let [cmdline (kill-mongo-process "mongodb://localhost:27017")]
      (is (not (nil? cmdline)))
      (Thread/sleep 15000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 4))
      (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
      (Thread/sleep 5000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 5))
      )))

(deftest test-crash-mongo-process
  (testing "Check that we can successfully 'crash' (kill -9) a mongod and restart it"
    (let [cmdline (kill-mongo-process "mongodb://localhost:27017" true)]
      (is (not (nil? cmdline)))
      (Thread/sleep 15000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 4))
      (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
      (Thread/sleep 5000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 5))
      )))

(deftest test-simulate-maintenance
  (testing "Test that the simulate-maintenance function correct does a rolling restart"
    (let [num-mongods (num-active-rs-members "mongodb://localhost:27017")]
      (simulate-maintenance "mongodb://localhost:27017")
      (Thread/sleep 5000)
      (is (= (num-active-rs-members "mongodb://localhost:27017") num-mongods)))))
