(ns tester-core.ssh-test-service-replicaset
  (:require [clojure.test :refer :all]
            [tester-core.core :refer :all]
            [tester-core.test-helpers :refer :all]
            [taoensso.timbre :as timbre :refer [debug error]]))

(defn- ssh-test-fixture
  [f]
  (let [servers ["rs1.mongodb.test" "rs2.mongodb.test" "rs3.mongodb.test"]]
    (is (= 0 (num-running-mongo-processes servers)))
    (ssh-apply-command-to-rs-servers "sudo systemctl start mongod" servers)
    (Thread/sleep 1500)
    (if (wait-test-rs-ready "mongodb://rs1.mongodb.test:27107,rs2.mongodb.test:27107,rs3.mongodb.test:27107/?replicaSet=replTestService&connectTimeoutMS=1000&serverSelectionTimeoutMS" 3 17)
      (f)
      (timbre/error "Test replica set not ready in time"))
    (ssh-apply-command-to-rs-servers "sudo systemctl stop mongod" servers)
    (wait-mongo-shutdown servers 20)))

(use-fixtures :each ssh-test-fixture)
(use-fixtures :once setup-logging-fixture)

(deftest test-get-rs-topology
  (testing "Check that we retrieve the correct primary and secondaries from the replset status"
    (let [primary      (get (get-rs-primary "mongodb://rs1.mongodb.test:27107") :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries "mongodb://rs1.mongodb.test:27107")))]
      (timbre/debug "Remote primary is " primary)
      (timbre/debug "Remote secondaries are " secondaries)
      (is (not (nil? (re-matches #"rs[1-3].mongodb.test:27107" primary))))
      (is (not (some #{primary} secondaries)))
      (is (= (count secondaries) 2)))))

(deftest test-rs-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (let [rs-uri "mongodb://rs1.mongodb.test:27107,rs2.mongodb.test:27107,rs3.mongodb.test:27107/?replicaSet=replTestService"
          original-primary (get (get-rs-primary rs-uri) :name)]
      (is original-primary)
      (timbre/debug "Current primary is " original-primary)
      (trigger-election rs-uri)
      (Thread/sleep 11000)
      (is (not (= (get (get-rs-primary rs-uri) :name) original-primary))))))


(deftest test-remote-degrade-rs
  (testing "Check that we can make a remote RS degraded (requires auth on remote RS"
    (let [rs-uri "mongodb://rs1.mongodb.test:27107,rs2.mongodb.test:27107,rs3.mongodb.test:27107/?replicaSet=replTestService"
          restart-cmd (make-rs-degraded rs-uri) ]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 8000)
      (is (not (replicaset-degraded? rs-uri))))))
    
(deftest test-remote-read-only-rs
  (testing "Check that we are able to successfully make a replica set read only
            and restore it afterwards"
    (let [rs-uri "mongodb://rs1.mongodb.test:27107,rs2.mongodb.test:27107,rs3.mongodb.test:27107/?replicaSet=replTestService"
          restart-cmd (make-rs-read-only rs-uri)]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 20000)
      (is (= (num-active-rs-members rs-uri) 1))
      (is (replica-set-read-only? rs-uri))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members rs-uri) 3))
      )))
