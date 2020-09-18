(ns mdb-test-yaml-driver.core-test
  (:require [clojure.test :refer :all]
            [mdb-test-yaml-driver.impl :as impl]
            [clojure.java.io :as io]))

(deftest parse-test-settings-empty-test
  (testing "Check that the test setting parser generates the correct sparse map for"
    (let [config-map {}
          result-map (impl/parse-test-settings config-map)]
      (is (= (count result-map) 0)))))

(deftest parse-test-settings-single-entry-test
  (testing "Check that the test setting parser generates the correct sparse map for"
    (let [config-map { :user "admin" }
          result-map (impl/parse-test-settings config-map)]
      (is (= (count result-map) 1))
      (is (= "admin" (get result-map :user)))
      (is (nil? (get result-map :random-entry))))))

(deftest parse-test-settings-single-entry-test
  (testing "Check that the test setting parser generates the correct sparse map for"
    (let [config-map { :user "admin" :wait-between-tests 120 :client-cert "my client cert" }
          result-map (impl/parse-test-settings config-map)]
      (is (= (count result-map) 2))
      (is (= "admin" (get result-map :user)))
      (is (= "my client cert" (get result-map :client-cert)))
      (is (nil? (get result-map :wait-between-tests)))
      (is (nil? (get result-map :random-entry))))))

(deftest parse-basic-yaml-test
  (testing "Check that we can parse the basic YAML file and extract the necessary information"
    (is (.exists (io/file "resources/basic-test.yaml")))
    (let [filename    "resources/basic-test.yaml"
          parsed-file (impl/parse (list filename))]
      ;;(println "Parsed file is " parsed-file)
      ;;(println "Type is " (type parsed-file))
      (is (not (nil? parsed-file)))
      (is (= 4 (count parsed-file)))
      (is (contains? (first parsed-file) :Config)))))
