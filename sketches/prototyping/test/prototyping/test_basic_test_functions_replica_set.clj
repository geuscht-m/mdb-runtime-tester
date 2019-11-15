(ns prototyping.test-basic-test-functions-replica-set
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [clojure.java.shell :refer [sh]]
            [clojure.pprint :refer :all]))

(defn- start-test-rs
  []
  ;;(println (System/getenv "PATH"))
  (let [homedir (System/getenv "HOME")]
    (sh "mlaunch" "start" "--dir" (str homedir "/tmp/mdb-test-rs"))))

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

(use-fixtures :once wrap-rs-tests)

(deftest test-degraded-rs
  (testing "Check that we can successfully degrade an RS by stopping one of the members"
    (let [restart-cmd (make-rs-degraded "mongodb://localhost:27017") ]
      (not (nil? restart-cmd))
      (Thread/sleep 30000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 2))
      (Thread/sleep 1000)
      (restart-cmd)
      (Thread/sleep 5000)
      (is (= (num-active-rs-members "mongodb://localhost:27018") 3)))))
