(ns mdb-test-yaml-driver.core-test
  (:require [clojure.test :refer :all]
            [mdb-test-yaml-driver.impl :as impl]
            [clojure.java.io :as io]))

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
