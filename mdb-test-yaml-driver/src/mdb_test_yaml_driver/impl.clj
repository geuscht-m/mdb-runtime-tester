(ns mdb-test-yaml-driver.impl
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str])
  (:use     [tester-core.core]))

(defn parse-test-settings
  "Parses out test settings from the interpreted YAML section and returns
   the non-nil fields for later merging"
  [config-map]
  ;; (println "Parsing test settings" config-map ", type of parameter is " (type config-map))
  (let [all-configs { :user (get config-map :user)
                    :pwd (get config-map :password)
                    :root-ca (get config-map :root-ca)
                    :ssl (get config-map :ssl)
                    :client-cert (get config-map :client-cert)
                     :auth-mechanism (get config-map :auth-mechanism) }]
    (println "all-configs is " all-configs)
    (into {} (filter second all-configs))))

(defn parse-runner-settings
  "Parses the test runner settings and returns a (potentially empty) map with
   the settings"
  [config-map]
  (into {} (filter (comp some? val)
                   { :wait-until-rollback (get config-map :wait-until-rollback)
                    :wait-between-tests   (get config-map :wait-between-tests) })))

(defn parse-config
  "Parse out the default Config section and populate the main configuration
   information from it"
  [config-map]
  ;;(println "Trying to parse config map " config-map)
  (if-let [main-config   (into {} (get config-map :Config))]
    (let [runner-config (parse-runner-settings main-config)
          test-config   (parse-test-settings   main-config)]
      (println "parsed test-config is " test-config)
      { :runner-config runner-config :test-config test-config })
    { :runner-config nil :test-config nil }))

(defn exec-test-member
  "Execute single test (test-element) with a configuration derived from both
   main-test-config and test specific configuration derived from the test
   configuration itself"
  [test-element main-test-config main-runner-config]
  (let [merged-test-configs   (merge main-test-config (parse-test-settings test-element))
        merged-runner-configs (merge main-runner-config (parse-runner-settings test-element))]
    ;; (println "Test element is " test-element)
    ;; (println "Main test config is " main-test-config)
    (println "Merged test config is " merged-test-configs)
    ;; (println "Merged runner config is " merged-runner-configs)
    (if (= (get test-element :operation) "make-degraded")
      (cond (contains? test-element :sharded-cluster) (println "Degrading sharded cluster at " (get test-element :sharded-cluster))
            (contains? test-element :replicaset)      (let [restart-cmd     (tester-core.core/make-rs-degraded (get test-element :replicaset) merged-test-configs)
                                                            revert-interval (if (nil? (get merged-runner-configs :wait-until-rollback)) 30 (get merged-runner-configs :wait-until-rollback))]
                                                        (Thread/sleep (* revert-interval 1000))
                                                        (restart-cmd)))
      (let [test-name (get test-element :operation)]
        ;; (println "Type of test-element is " (type test-element))
        ;; (println "Trying to resolve symbol " test-name)
        (let [testf (resolve (symbol "tester-core.core" (get test-element :operation)))]
          ;; (println "Calling symbol " testf)
          (if (nil? testf)
            (println "Error - unrecognised test function " (get test-element :operation))
            (testf (or (get test-element :replicaset) (get test-element :sharded-cluster))
                       :user (get merged-test-configs :user)
                       :pwd (get merged-test-configs :pwd)
                       :root-ca (get merged-test-configs :root-ca)
                       :ssl (get merged-test-configs :ssl)
                       :client-cert (get merged-test-configs :client-cert)
                       :auth-mechanism (get merged-test-configs :auth-mechanism))))))))

(defn exec-test
  [test main-test-config main-runner-config]
  ;; (println "Processing test " test)
  ;; (println "exec-test main-test-config is: " main-test-config)
  (if-let [test-case (get test :Test)]
    (exec-test-member (first test-case) main-test-config main-runner-config)
    "Malformed test entry, no Test section found"))

(defn run-tests
  [tests & { :keys [testrunner-config test-config] :or {testrunner-config nil test-config nil}}]
  ;;(println "run-tests test-config is " test-config)
  (let [test-interval (if (or (nil? testrunner-config)
                              (nil? (get testrunner-config :wait-between-tests)))
                        60
                        (get testrunner-config :wait-between-tests))]
    ;;(println (type test-interval) " " test-interval)
    ;;(println testrunner-config)
    ;;(println test-config)
    ;;(println (get config :wait-between-tests))
    ;;(println (type config))
    ;;(println (type tests))
    ;;(println "Tests are:\n" tests "\n\n")
    (doall (map (fn [t]
                  (let [result (exec-test t test-config testrunner-config)]
                    ;;(println "Sleeping for " test-interval " seconds")
                    (Thread/sleep (* test-interval 1000))
                    result))
                  tests))))

(defn execute-tests
  [tests]
  ;; (println "execute-tests, tests are: " tests)
  ;; (println "Config is " (get (first tests) :Config))
  (if (nil? (get (first tests) :Config))
    (do ;;(println "Running tests without default parameters")
        (run-tests tests))
    (let [main-config  (parse-config (first tests))
          test-results (run-tests (rest tests) :testrunner-config (get main-config :runner-config) :test-config (get main-config :test-config))]      
      ;;(println "Executing tests, tests are" tests)
      test-results)))

(defn parse
  [args]
  (if (= (count args) 1)
    (let [yaml-string (str/split (slurp (first args)) #"---")]
      ;;(println yaml-string)
      (filter (fn [x] (not (nil? x))) (map yaml/parse-string yaml-string)))
    (println "Too many parameters, program only accepts a single test file, parameters passed is " args " with count " (count args))))

