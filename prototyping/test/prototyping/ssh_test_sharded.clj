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

(deftest test-remote-rs-degradation
  (testing "Make sure we can shut down and restart a random remote replica set member"
    (let [rs-uri "mongodb://replTest/rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test"
          restart-cmd (make-rs-degraded rs-uri) ]
      (not (nil? restart-cmd))
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (not (replicaset-degraded? rs-uri)))))
