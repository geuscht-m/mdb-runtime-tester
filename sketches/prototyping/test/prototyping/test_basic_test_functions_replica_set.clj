(ns prototyping.test-basic-test-functions-replica-set
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [clojure.java.shell :refer [sh]]))

(defn- start-test-rs
  []
  (println (sh "/usr/bin/mlaunch" "start" "--dir" "/home/timo/tmp/mdb-test-rs")))

(defn- stop-test-rs
  []
  (println (sh "/usr/bin/mlaunch" "stop" "--dir" "/home/timo/tmp/mdb-test-rs")))

(defn- wrap-rs-tests
  [f]
  (start-test-rs)
  (Thread/sleep 15000) ;; Let the RS stablize
  (f)
  (stop-test-rs))

(use-fixtures :each wrap-rs-tests)

;; (deftest test-rs-maintenance
;;   (testing "Check that simulated maintenance of the replica set acts as expected"
;;     (simulate-maintenance "mongodb://localhost:27017")))

(deftest test-degraded-rs
  (testing "Check that we can successfully degrade an RS by stopping one of the members"
    (println (make-rs-degraded "mongodb://localhost:27017"))
    (Thread/sleep 60000)))

(deftest test-stepdown
  (testing "Check that stepping down the primary on an RS works"
    (println (trigger-election "mongodb://localhost:27017"))
    (Thread/sleep 10000)))
