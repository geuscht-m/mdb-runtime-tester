(ns mdb-test-yaml-driver.core-test
  (:require [clojure.test :refer :all]
            [mdb-test-yaml-driver.impl :as impl]
            [clojure.java.io :as io]))

(deftest parse-test-settings-empty-test
  (testing "Check that the test setting parser generates the correct sparse map for"
    (let [config-map (clojure.lang.PersistentHashMap/EMPTY)
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

(defn- mock-trigger-election
  [rs-uri & { :keys [forced user pwd ssl root-ca client-cert auth-mechanism] :or {forced false user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil}}]
  ;;(println "Calling mock-trigger-election")
  (let [results { :rs-uri rs-uri :forced forced :user user :pwd pwd :ssl ssl :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism }]
    ;;(println "mock-trigger-election results are " results)
    results))

(deftest basic-stepdown-test
  (testing "Check that we correctly parse trigger-election call and pass in the correct parameters"
    (is (.exists (io/file "resources/trigger-test.yaml")))
    (with-redefs-fn {#'tester-core.core/trigger-election mock-trigger-election}
      #(let [parsed-file (impl/parse         (list "resources/trigger-test.yaml"))
             results     (impl/execute-tests parsed-file)]
         ;; (println "parsed-file is " parsed-file)
         ;;(println "basic-stepdown-test results are " results)
         (is (= 1 (count results)))
         (is (= "mongodb://localhost:27017,localhost:27018,localhost:27019/" (:rs-uri (first results))))))))

(deftest basic-stepdown-test-with-config
  (testing "Check that we correctly parse trigger-election call and pass in the correct parameters, this time with SSL enabled in the Config section"
    (is (.exists (io/file "resources/trigger-test-with-config.yaml")))
    (with-redefs-fn {#'tester-core.core/trigger-election mock-trigger-election}
      #(let [parsed-file (impl/parse         (list "resources/trigger-test-with-config.yaml"))
             results     (impl/execute-tests parsed-file)]
         ;; (println "parsed-file is " parsed-file)
         ;;(println "basic-stepdown-test-with-config results are " results)
         (is (= 1 (count results)))
         (is (= "mongodb://localhost:27017,localhost:27018,localhost:27019/" (:rs-uri (first results))))
         (is (= true (:ssl (first results))))))))

