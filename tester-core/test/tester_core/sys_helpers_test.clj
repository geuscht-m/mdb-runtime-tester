(ns tester-core.sys-helpers-test
  (:require [tester-core.sys-helpers :refer :all]
            [tester-core.test-helpers :refer [ssh-apply-command-to-rs-servers]]
            [tester-core.core :refer [run-server-status]]
            [clojure.test :refer :all]
            [clojure.pprint :refer :all]))

(deftest test-get-process-list
  (testing "Check that we can get the list of processes"
    (let [proc-list (get-process-list)]      
      (not (nil? proc-list))
      (is (> (count proc-list) 1))
      (is (= (compare (keys (first proc-list)) (:pid :command-line))))      
    )))

(deftest test-get-os-type
  (testing "Check the result of get-os-type"
    (let [os-type (get-os-type)]
      (is (or (= os-type "Linux") (= os-type "Mac OS X") (= os-type "Windows"))))))

(deftest test-get-os-type-remote
  (testing "Check the result of remote get-os-type"
    (let [os-type (get-os-type "rs1.mongodb.test")]
      (is (= os-type "Linux")))))

(deftest test-is-not-service
  (testing "Check if remote MongoDB was started as a service or not"
    (ssh-apply-command-to-rs-servers "./start-mongod-replset-node.sh" (list "rs1.mongodb.test"))
    (let [pid (:pid (run-server-status "mongodb://rs1.mongodb.test:27017" :user "admin" :pwd "pw99"))
          result (check-if-service "rs1.mongodb.test" pid)]
      (is (= result false)))
    (ssh-apply-command-to-rs-servers "pkill mongod" (list "rs1.mongodb.test"))))


(deftest test-is-service
  (testing "Make sure we correcty identify a mongod that was started as a service"
    (ssh-apply-command-to-rs-servers "sudo systemctl start mongod" (list "rs1.mongodb.test"))
    (let [pid (:pid (run-server-status "mongodb://rs1.mongodb.test:27107"))
          result (check-if-service "rs1.mongodb.test" pid)]
      (is (= result true)))
    (ssh-apply-command-to-rs-servers "sudo systemctl stop mongod" (list "rs1.mongodb.test"))))
