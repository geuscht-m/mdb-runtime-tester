;;
;; NOTE: In contrast to the other tests, this test suite
;;       can't launch its own environments and expects
;;       to use the vagrant setup in this git repo.
;;       It requires a replica set on three hosts,
;;       rs[1-3].mongodb.test, running on default
;;       ports
(ns tester-core.ssh-test-replicaset
  (:require [clojure.test :refer :all]
            [tester-core.core :refer :all]
            [tester-core.test-helpers :refer :all]))

(defn- start-remote-mongods
  [servers]
  (ssh-apply-command-to-rs-servers "mongod -f mongod-ssh-rs.conf" servers))

(defn- stop-remote-mongods
  [servers]
  (ssh-apply-command-to-rs-servers "pkill mongod" servers))

(defn- ssh-test-fixture
  [f]
  (let [servers ["rs1.mongodb.test" "rs2.mongodb.test" "rs3.mongodb.test"]]
    (start-remote-mongods servers)
    (Thread/sleep 1500)
    (if (wait-test-rs-ready "mongodb://rs1.mongodb.test:27017,rs2.mongodb.test:27017,rs3.mongodb.test:27017/?replicaSet=replTest&connectTimeoutMS=1000" 3 17 :user "admin" :pwd "pw99")
      (f)
      (println "Test replica set not ready in time"))
    (stop-remote-mongods servers)
    (Thread/sleep 650)))

(use-fixtures :each ssh-test-fixture)

(deftest test-is-mongos-process
  (testing "Check if we're running against a mongos process - should fail as we're running mongod"
    (is (not (is-mongos-process? "mongodb://rs1.mongodb.test" :user "admin" :pwd "pw99")))))

(deftest test-is-mongod-process
  (testing "Check if we're running against a mongod process"
    (is (is-mongod-process? "mongodb://rs2.mongodb.test" :user "admin" :pwd "pw99"))))

(deftest test-is-sharded
  (testing "Are we connected to a sharded cluster"
    (is (not (is-sharded-cluster? "mongodb://rs1.mongodb.test:27017,rs2.mongodb.test:27017,rs3.mongodb.test:27017/?replicaSet=replTest" :user "admin" :pwd "pw99")))))


;; TODO - check why this call doesn't work against 3.6 w/ auth but does work in 4.0 w/o auth
;; (deftest test-is-sharded
;;   (testing "Are we connected to a sharded cluster"
;;     (is (not (is-sharded-cluster? "mongodb://rs3.mongodb.test" "admin" "pw99")))))

(deftest test-get-rs-topology
  (testing "Check that we retrieve the correct primary and secondaries from the replset status"
    (let [primary      (get (get-rs-primary "mongodb://rs1.mongodb.test" :user "admin" :pwd "pw99") :name)
          secondaries  (sort (map #(get % :name) (get-rs-secondaries "mongodb://rs1.mongodb.test" :user "admin" :pwd "pw99")))]
      ;;(println "Remote primary is " primary)
      ;;(println "Remote secondaries are " secondaries)
      (is (not (nil? (re-matches #"rs[1-3].mongodb.test:27017" primary))))
      (is (not (some #{primary} secondaries)))
      (is (= (count secondaries) 2)))))

(deftest test-remote-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (let [rs-uri "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          user             "admin"
          pw               "pw99"
          original-primary (get (get-rs-primary rs-uri :user user :pwd pw) :name)]
      (trigger-election rs-uri :user user :pwd pw)
      (Thread/sleep 11000)
      (is (not (= (get (get-rs-primary rs-uri :user user :pwd pw) :name) original-primary))))))


(deftest test-remote-degrade-rs
  (testing "Check that we can make a remote RS degraded (requires auth on remote RS"
    (let [rs-uri "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          user   "admin"
          pw     "pw99"
          restart-cmd (make-rs-degraded rs-uri :user user :pwd pw) ]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 30000)
      (is (replicaset-degraded? rs-uri :user user :pwd pw))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 8000)
      (is (not (replicaset-degraded? rs-uri :user user :pwd pw))))))
    
(deftest test-remote-read-only-rs
  (testing "Check that we are able to successfully make a replica set read only
            and restore it afterwards"
    (let [rs-uri "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          user   "admin"
          pw     "pw99"
          restart-cmd (make-rs-read-only rs-uri :user user :pwd pw)]
      (is (not (nil? restart-cmd)))
      (Thread/sleep 20000)
      (is (= (num-active-rs-members rs-uri :user user :pwd pw) 1))
      (is (replica-set-read-only? rs-uri :user user :pwd pw))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members rs-uri :user user :pwd pw) 3))
      )))

(deftest test-kill-mongo-process
  (testing "Check that we can shut down a remote mongo process using a signal instead of the
            mongo command"
    (let [rs-uri  "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          uri     "mongodb://rs1.mongodb.test:27017"
          user    "admin"
          pwd     "pw99"]
      (is (= 3 (num-active-rs-members rs-uri :user user :pwd pwd)))
      (let [cmdline (kill-mongo-process uri :user user :pwd pwd)]
        (is (not (nil? cmdline)))
        (Thread/sleep 12000)
        (is (= 2 (num-active-rs-members rs-uri :user user :pwd pwd)))
        (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
        (Thread/sleep 7000)
        (is (= 3 (num-active-rs-members rs-uri :user user :pwd pwd))))
      )))

(deftest test-crash-mongo-process
  (testing "Check that we can successfully 'crash' (kill -9) a mongod and restart it"
    (let [rs-uri  "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          uri     "mongodb://rs3.mongodb.test"
          user    "admin"
          pwd     "pw99"]
      (is (= 3 (num-active-rs-members rs-uri :user user :pwd pwd)))
      (let [cmdline (kill-mongo-process uri :force true :user user :pwd pwd)]
        (is (not (nil? cmdline)))
        (Thread/sleep 15000)
        (is (= 2 (num-active-rs-members rs-uri :user user :pwd pwd)))
        (start-mongo-process (get cmdline :uri) (get cmdline :cmd-line))
        (Thread/sleep 5000)
        (is (= 3 (num-active-rs-members rs-uri :user user :pwd pwd)))
      ))))

(deftest test-remote-simulate-maintenance
  (testing "Test that the simulate-maintenance function correct does a rolling restart and works with authentication"
    (let [rs-uri "mongodb://rs1.mongodb.test,rs2.mongodb.test,rs3.mongodb.test/?replicaSet=replTest"
          user   "admin"
          pw     "pw99"
          num-mongods (num-active-rs-members rs-uri :user user :pwd pw)]
      (is (= num-mongods (num-active-rs-members rs-uri :user user :pwd pw)))
      (simulate-maintenance rs-uri :user user :pwd pw)
      (Thread/sleep 15000)
      (is (= num-mongods (num-active-rs-members rs-uri :user user :pwd pw))))))
