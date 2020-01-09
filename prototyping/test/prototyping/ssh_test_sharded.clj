;;
;; NOTE: In contrast to the other tests, this test suite
;;       can't launch its own environments and expects
;;       to use the vagrant setup in this git repo.
;;       It requires a replica set on three hosts,
;;       rs[1-3].mongodb.test, running on default
;;       ports
(ns prototyping.ssh-test-sharded
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [prototyping.test-helpers :refer :all]))

(deftest test-remote-rs-kill-single
  (testing "Make sure we can shut down and restart a random remote replica set member"
    (let [rs-uri "mongodb://replTest/rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test"
          user   "admin"
          pw     "pw99"
          ;;restart-cmd (make-rs-degraded rs-uri) ]
          restart-info (kill-mongo-process "mongodb://rs2.mongodb.test" user pw)]
      (not (nil? restart-info))
      (println "Restart info is " restart-info)
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri user pw))
      (Thread/sleep 1000)
      (start-mongo-process (get restart-info :uri) (get restart-info :cmd-line))
      (Thread/sleep 5000)
      (not (replicaset-degraded? rs-uri user pw)))))

(deftest test-remote-degrade-rs
  (testing "Check that we can make a remote RS degraded (requires auth on remote RS"
    (let [rs-uri "mongodb://replTest/rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test"
          user   "admin"
          pw     "pw99"
          restart-cmd (make-rs-degraded rs-uri user pw) ]
      (not (nil? restart-cmd))
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri user pw))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (not (replicaset-degraded? rs-uri user pw)))))
    
