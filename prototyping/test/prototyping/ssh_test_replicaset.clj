;;
;; NOTE: In contrast to the other tests, this test suite
;;       can't launch its own environments and expects
;;       to use the vagrant setup in this git repo.
;;       It requires a replica set on three hosts,
;;       rs[1-3].mongodb.test, running on default
;;       ports
(ns prototyping.ssh-test-replicaset
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [prototyping.test-helpers :refer :all]))

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should fail as we're running mongod"
    (is (not (is-mongos-process? "mongodb://rs1.mongodb.test" "admin" "pw99")))))

(deftest test-is-mongod-process
  (testing "Check if we're running against a mongod process"
    (is (is-mongod-process? "mongodb://rs2.mongodb.test" "admin" "pw99"))))


;; TODO - check why this call doesn't work against 3.6 w/ auth but does work in 4.0 w/o auth
;; (deftest test-is-sharded
;;   (testing "Are we connected to a sharded cluster"
;;     (is (not (is-sharded-cluster? "mongodb://rs3.mongodb.test" "admin" "pw99")))))

(deftest test-get-rs-topology
  (testing "Check that we retrieve the correct primary and secondaries from the replset status"
    (let [primary      (get (get-rs-primary "mongodb://rs1.mongodb.test" "admin" "pw99") :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries "mongodb://rs1.mongodb.test" "admin" "pw99")))]
      (println primary)
      (println secondaries)
      (not (nil? (re-matches #"mongodb://rs[1-3].mongodb.test" primary)))
      (not (some #{primary} secondaries))
      (is  (= (count secondaries) 2)))))

(deftest test-remote-rs-kill-single
  (testing "Make sure we can shut down and restart a random remote replica set member"
    (let [rs-uri "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          user   "admin"
          pw     "pw99"
          ;;restart-cmd (make-rs-degraded rs-uri) ]
          restart-info (kill-mongo-process "mongodb://rs2.mongodb.test" user pw)]
      (is (not (nil? restart-info)))
      (println "Restart info is " restart-info)
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri user pw))
      (Thread/sleep 1000)
      (start-mongo-process (get restart-info :uri) (get restart-info :cmd-line))
      (Thread/sleep 5000)
      (is (not (replicaset-degraded? rs-uri user pw))))))

(deftest test-remote-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (let [user             "admin"
          pw               "pw99"
          original-primary (get (get-rs-primary "mongodb://rs1.mongodb.test" user pw) :name)]
      (trigger-election "mongodb://rs1.mongodb.test" user pw)
      (Thread/sleep 11000)
      (is (not (= (get (get-rs-primary "mongodb://rs1.mongodb.test" user pw) :name) original-primary))))))


(deftest test-remote-degrade-rs
  (testing "Check that we can make a remote RS degraded (requires auth on remote RS"
    (let [rs-uri "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          user   "admin"
          pw     "pw99"
          restart-cmd (make-rs-degraded rs-uri user pw) ]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri user pw))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 8000)
      (is (not (replicaset-degraded? rs-uri user pw))))))
    
(deftest test-remote-read-only-rs
  (testing "Check that we are able to successfully make a replica set read only
            and restore it afterwards"
    (let [rs-uri "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          user   "admin"
          pw     "pw99"
          restart-cmd (make-rs-read-only rs-uri user pw)]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 20000)
      (is (= (num-active-rs-members rs-uri user pw) 1))
      (is (replica-set-read-only? rs-uri user pw))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members rs-uri user pw) 3))
      )))
