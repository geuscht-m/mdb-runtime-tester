(ns prototyping.core-test
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]))

(deftest test-config-servers-uri
  (testing "Checking if we get the correct list of config servers"
    (is (= (get-config-servers-uri "mongodb://localhost:27017") nil))))

(deftest test-shard-uris
  (testing "Correct retrieval of shard member uris"
    (is (= 0 1))))

(deftest test-rs-member-retrieval
  (testing "Make sure we get all the info about the replica set members"
    (is (= 0 9))))
