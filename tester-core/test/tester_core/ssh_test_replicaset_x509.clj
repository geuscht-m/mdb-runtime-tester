;;
;; NOTE: In contrast to the other tests, this test suite
;;       requires the preconfigured vagrant test setup in this git repo.
;;       It will start and stop the replica set as needed, but the
;;       configuration and binaries have to be in place

(ns tester-core.ssh-test-replicaset-x509
  (:require [clojure.test :refer :all]
            [tester-core.core :refer :all]
            [tester-core.test-helpers :refer :all]
            [tester-core.mini-driver :as md :refer :all]
            [taoensso.timbre :as timbre :refer [debug error]]))

(defn- ssh-test-fixture
  [f]
  (let [servers ["rs1.mongodb.test" "rs2.mongodb.test" "rs3.mongodb.test"]]
    (is (= 0 (num-running-mongo-processes servers)))
    (ssh-apply-command-to-rs-servers "mongod -f mongod-ssh-x509.conf" servers)
    (Thread/sleep 1500)
    (if (wait-test-rs-ready "mongodb://rs1.mongodb.test:29017,rs2.mongodb.test:29017,rs3.mongodb.test:29017/?replicaSet=replTestX509&connectTimeoutMS=1000&serverSelectionTimeoutMS=1000&ssl=true" 3 17 :client-cert "../../../tls/user-cert.pem" :ssl true :root-ca "../../../tls/root.crt" :auth-mechanism :mongodb-x509)
      (f)
      (timbre/error "Test replica set not ready in time"))
    (ssh-apply-command-to-rs-servers "pkill mongod" servers)    
    (wait-mongo-shutdown servers 20)))

(use-fixtures :each ssh-test-fixture)
(use-fixtures :once setup-logging-fixture)

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should fail as we're running mongod"
    (is (not (is-mongos-process? "mongodb://rs1.mongodb.test:29017/?ssl=true" :root-ca "../../../tls/root.crt" :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509)))))

(deftest test-is-mongod-process
  (testing "Check if we're running against a mongod process"
    (is (is-mongod-process? "mongodb://rs2.mongodb.test:29017/?ssl=true" :root-ca "../../../tls/root.crt" :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509))))


;; TODO - check why this call doesn't work against 3.6 w/ auth but does work in 4.0 w/o auth
;; (deftest test-is-sharded
;;   (testing "Are we connected to a sharded cluster"
;;     (is (not (is-sharded-cluster? "mongodb://rs3.mongodb.test" "admin" "pw99")))))

(deftest test-get-rs-topology
  (testing "Check that we retrieve the correct primary and secondaries from the replset status"
    (let [rs-uri       "mongodb://rs1.mongodb.test:29017,rs2.mongodb.test:29017,rs3.mongodb.test:29017/?replicaSet=replTestX509&connectTimeoutMS=1000&ssl=true"
          conn         (md/mdb-connect rs-uri :root-ca "../../../tls/root.crt" :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509)
          primary      (get (get-rs-primary conn) :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries conn)))]
      (md/mdb-disconnect conn)
      ;;(println "Remote primary is " primary)
      ;;(println "Remote secondaries are " secondaries)
      (is (not (nil? (re-matches #"rs[1-3].mongodb.test:29017" primary))))
      (is (not (some #{primary} secondaries)))
      (is (= (count secondaries) 2)))))

(deftest test-remote-rs-kill-single
  (testing "Make sure we can shut down and restart a random remote replica set member"
    (let [rs-uri  "mongodb://rs1.mongodb.test:29017,rs2.mongodb.test:29017,rs3.mongodb.test:29017/?replicaSet=replTestX509&ssl=true"
          root-ca "../../../tls/root.crt"
          ;;restart-cmd (make-rs-degraded rs-uri) ]
          restart-info (kill-mongo-process "mongodb://rs2.mongodb.test:29017/?ssl=true" :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509)]
      (is (not (nil? restart-info)))
      ;;(println "Restart info is " restart-info)
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509))
      (Thread/sleep 1000)
      (start-mongo-process (get restart-info :uri) (get restart-info :cmd-line))
      (Thread/sleep 5000)
      (is (not (replicaset-degraded? rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509))))))

(deftest test-remote-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (let [rs-uri  "mongodb://rs1.mongodb.test:29017,rs2.mongodb.test:29017,rs3.mongodb.test:29017/?replicaSet=replTestX509&ssl=true"
          root-ca          "../../../tls/root.crt"
          original-primary (get (get-rs-primary rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509) :name)]
      (trigger-election rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509)
      (Thread/sleep 11000)
      (is (not (= (get (get-rs-primary rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509) :name) original-primary))))))

(deftest test-remote-degrade-rs
  (testing "Check that we can make a remote RS degraded (requires auth on remote RS"
    (let [rs-uri "mongodb://rs1.mongodb.test:29017,rs2.mongodb.test:29017,rs3.mongodb.test:29017/?replicaSet=replTestX509&ssl=true"
          root-ca          "../../../tls/root.crt"
          restart-cmd (make-rs-degraded rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509) ]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 8000)
      (is (not (replicaset-degraded? rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509))))))
    
(deftest test-remote-read-only-rs
  (testing "Check that we are able to successfully make a replica set read only
            and restore it afterwards"
    (let [rs-uri  "mongodb://rs1.mongodb.test:29017,rs2.mongodb.test:29017,rs3.mongodb.test:29017/?replicaSet=replTestX509&ssl=true"
          root-ca "../../../tls/root.crt"
          restart-cmd (make-rs-read-only rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509)]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 20000)
      (is (= (num-active-rs-members rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509) 1))
      (is (replica-set-read-only? rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members rs-uri :root-ca root-ca :client-cert "../../../tls/user-cert.pem" :auth-mechanism :mongodb-x509) 3))
      )))
