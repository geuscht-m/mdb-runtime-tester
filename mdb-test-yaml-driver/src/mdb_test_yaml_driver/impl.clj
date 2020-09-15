(ns mdb-test-yaml-driver.impl
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str])
  (:use     [tester-core.core]))

(defn parse-config
  [config-map]
  ;;(println config-map)
  (get config-map :Config))

(defn exec-test-member
  [test-element]
  (println "Test element is " test-element)
  (if (= (get test-element :operation) "make-degraded")
    (cond (contains? test-element :sharded-cluster) (println "Degrading sharded cluster at " (get test-element :sharded-cluster))
          (contains? test-element :replicaset) (if (contains? test-element :user)
                                                 (let [restart-cmd     (tester-core.core/make-rs-degraded (get test-element :replicaset) :user (get test-element :user) :pwd (get test-element :password))
                                                       revert-interval (if (nil? (get test-element :wait-until-rollback)) 30 (get test-element :wait-until-rollback))]
                                                   (Thread/sleep (* revert-interval 1000))
                                                   (restart-cmd))
                                                 (tester-core.core/make-rs-degraded (get test-element :replicaset))))
    (let [test-name (get test-element :operation)]
      (println "Trying to resolve symbol " test-name)
      (let [testf (resolve (symbol "tester-core.core" (get test-element :operation)))]
        (if (nil? testf)
          (println "Error - unrecognised test function " (get test-element :operation))
          (testf (or (get test-element :replicaset) (get test-element :sharded-cluster))))))))

(defn exec-test
  [test]
  (if-let [test-case (get test :Test)]
    (doall (map exec-test-member test-case))
    "Malformed test entry, no Test section found"))

(defn run-tests
  [tests & { :keys [config] :or {config nil}}]
  (let [test-interval (if (or (nil? config) (nil? (get config :wait-between-tests))) 60 (get config :wait-between-tests))]
    (println (type test-interval) " " test-interval)
    (println config)
    ;;(println (get config :wait-between-tests))
    ;;(println (type config))
    ;;(println (type tests))
    (println "\n" tests "\n\n")
    (doall (map (fn [t]
                  (exec-test t)
                  (println "Sleeping for " test-interval " seconds")
                  (Thread/sleep (* test-interval 1000))) tests))))

(defn execute-tests
  [tests]
  (if (nil? (get (first tests) :Config))
    (run-tests tests)
    (run-tests (rest tests) :config (first (parse-config (first tests)))))
  (println tests))

(defn parse
  [args]
  (if (= (count args) 1)
    (let [yaml-string (str/split (slurp (first args)) #"---")]
      ;;(println yaml-string)
      (filter (fn [x] (not (nil? x))) (map yaml/parse-string yaml-string)))
    (println "Too many parameters, program only accepts a single test file")))

