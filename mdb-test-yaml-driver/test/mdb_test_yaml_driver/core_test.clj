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

(defn- mock-test-reflector
  "Mock for most of the regular test functions that only reflects back
   the parameters that were passed to it. Can be used to simulate most,
   but not all of the functions. Can't be used when specific return value
   would be expected, for example from the shutdown/restart functions"
  [rs-uri & { :keys [forced user pwd ssl root-ca client-cert auth-mechanism] :or {forced false user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil}}]
  ;;(println "Calling mock-trigger-election")
  (let [results { :rs-uri rs-uri :forced forced :user user :pwd pwd :ssl ssl :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism }]
    ;; (println "mock-test-reflector results are: " results)
    results))

(deftest basic-stepdown-test
  (testing "Check that we correctly parse trigger-election call and pass in the correct parameters"
    (is (.exists (io/file "resources/trigger-test.yaml")))
    (with-redefs-fn {#'tester-core.core/trigger-election mock-test-reflector}
      #(let [parsed-file (impl/parse         (list "resources/trigger-test.yaml"))
             results     (impl/execute-tests parsed-file)]
         ;; (println "parsed-file is " parsed-file)
         ;;(println "basic-stepdown-test results are " results)
         (is (= 1 (count results)))
         (is (= "mongodb://localhost:27017,localhost:27018,localhost:27019/" (:rs-uri (first results))))))))

(deftest basic-stepdown-test-with-config
  (testing "Check that we correctly parse trigger-election call and pass in the correct parameters, this time with SSL enabled in the Config section"
    (is (.exists (io/file "resources/trigger-test-with-config.yaml")))
    (with-redefs-fn {#'tester-core.core/trigger-election mock-test-reflector}
      #(let [parsed-file (impl/parse         (list "resources/trigger-test-with-config.yaml"))
             results     (impl/execute-tests parsed-file)]
         ;; (println "parsed-file is " parsed-file)
         ;;(println "basic-stepdown-test-with-config results are " results)
         (is (= 1 (count results)))
         (is (= "mongodb://localhost:27017,localhost:27018,localhost:27019/" (:rs-uri (first results))))
         (is (= true (:ssl (first results))))))))


(deftest basic-simulate-maintenance-with-all-config-test
  (testing "Check that we can pass in the full set of test parameters via the config section of a set of test cases"
    (is (.exists (io/file "resources/maintenance-test-with-config.yaml")))
    (with-redefs-fn {#'tester-core.core/simulate-maintenance mock-test-reflector}
      #(let [parsed-file (impl/parse         (list "resources/maintenance-test-with-config.yaml"))
             results     (impl/execute-tests parsed-file)]
         ;; (println "parsed-file is " parsed-file)
         ;; (println "basic-simulate-maintenance-with-all-config-test results are " results)
         (is (= 1 (count results)))
         (is (= "mongodb://localhost:27017,localhost:27018,localhost:27019/" (:rs-uri (first results))))
         (is (= true (:ssl (first results))))
         (is (= "test" (:user (first results))))
         (is (= "test" (:pwd (first results))))
         (is (= "MONGODB-X509" (:auth-mechanism (first results))))
         (is (= "test-root-ca.crt" (:root-ca (first results))))
         (is (= "my-client-cert.pem" (:client-cert (first results))))
         ))))

(deftest basic-simulate-maintenance-all-in-test-test
  (testing "Check that we can pass in the full set of test parameters via the test section of a set of a test case"
    (is (.exists (io/file "resources/maintenance-test.yaml")))
    (with-redefs-fn {#'tester-core.core/simulate-maintenance mock-test-reflector}
      #(let [parsed-file (impl/parse         (list "resources/maintenance-test.yaml"))
             results     (impl/execute-tests parsed-file)]
         ;; (println "parsed-file is " parsed-file)
         ;; (println "basic-simulate-maintenance-with-all-config-test results are " results)
         (is (= 1 (count results)))
         (is (= "mongodb://localhost:27017,localhost:27018,localhost:27019/" (:rs-uri (first results))))
         (is (= true (:ssl (first results))))
         (is (= "test" (:user (first results))))
         (is (= "test" (:pwd (first results))))
         (is (= "MONGODB-X509" (:auth-mechanism (first results))))
         (is (= "test-root-ca.crt" (:root-ca (first results))))
         (is (= "my-client-cert.pem" (:client-cert (first results))))
         ))))

(deftest basic-simulate-maintenance-with-override-test
  (testing "Check that we can pass in the full set of test parameters via the test section of a set of a test case"
    (is (.exists (io/file "resources/maintenance-test-with-override.yaml")))
    (with-redefs-fn {#'tester-core.core/simulate-maintenance mock-test-reflector}
      #(let [parsed-file (impl/parse         (list "resources/maintenance-test-with-override.yaml"))
             results     (impl/execute-tests parsed-file)]
         ;; (println "parsed-file is " parsed-file)
         ;; (println "basic-simulate-maintenance-with-all-config-test results are " results)
         (is (= 1 (count results)))
         (is (= "mongodb://localhost:27017,localhost:27018,localhost:27019/" (:rs-uri (first results))))
         (is (= true (:ssl (first results))))
         (is (= "test" (:user (first results))))
         (is (= "test" (:pwd (first results))))
         (is (= "MONGODB-X509" (:auth-mechanism (first results))))
         (is (= "test-root-ca.crt" (:root-ca (first results))))
         (is (= "my-client-cert.pem" (:client-cert (first results))))
         ))))
