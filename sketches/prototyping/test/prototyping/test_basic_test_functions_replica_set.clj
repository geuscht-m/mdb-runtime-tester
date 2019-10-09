(ns prototyping.test-basic-test-functions-replica-set
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :refer :all]))

(defn- start-test-rs
  []
  (println (System/getenv "PATH"))
  (let [homedir (System/getenv "HOME")]
    (println (sh "mlaunch" "start" "--dir" (str homedir "/tmp/mdb-test-rs")))))

(defn- stop-test-rs
  []
  (let [homedir (System/getenv "HOME")]
        (println (sh "mlaunch" "stop" "--dir" (str homedir "/tmp/mdb-test-rs")))))

(defn- wrap-rs-tests
  [f]
  (start-test-rs)
  (Thread/sleep 15000) ;; Let the RS stablize
  (f)
  (stop-test-rs))

(use-fixtures :each wrap-rs-tests)

(deftest test-rs-member-retrieval
  (testing "Make sure we get all the info about the replica set members"
    (is (= (count (get-rs-secondaries "mongodb://localhost:27017")) 2))))

;; (deftest test-rs-maintenance
;;   (testing "Check that simulated maintenance of the replica set acts as expected"
;;     (simulate-maintenance "mongodb://localhost:27017")))

(deftest test-degraded-rs
  (testing "Check that we can successfully degrade an RS by stopping one of the members"
    (let [restart-cmd (make-rs-degraded "mongodb://localhost:27017") ]
      (not (nil? restart-cmd))
      (Thread/sleep 10000)
      (println "\n\nTrying to run restart-cmd\n")
      (pprint restart-cmd)
      (println "\n\n")
      (restart-cmd))))

(deftest test-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (println (trigger-election "mongodb://localhost:27017"))
    (Thread/sleep 10000)))
