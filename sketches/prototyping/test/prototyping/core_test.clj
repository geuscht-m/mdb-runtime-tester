(ns prototyping.core-test
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]))

(deftest test-get-rs-topology
  (testing "Check that we retrieve the correct primary from the replset status"
    (is (= (get (get-rs-primary "mongodb://localhost:27017") :name) "localhost:27017"))
    (is (= (sort (map #(get % :name) (get-rs-secondaries "mongodb://localhost:27017"))) (sort (list "localhost:27018" "localhost:27019"))))))

;; (deftest test-config-servers-uri
;;   (testing "Checking if we get the correct list of config servers"
;;     (is (= (get-config-servers-uri "mongodb://localhost:27017") nil))))

;;(deftest test-shard-uris
;;  (testing "Correct retrieval of shard member uris"
;;    (is (= 0 1))))

(deftest test-rs-member-retrieval
  (testing "Make sure we get all the info about the replica set members"
    (is (= (count (get-rs-secondaries "mongodb://localhost:27017")) 2))))
