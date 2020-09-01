(ns mdb-test-yaml-driver.core
  (:require [tester-core.core :as tester]
            [clj-yaml.core :as yaml]
            [clojure.string :as str])
  (:gen-class))

(defn- parse-config
  [config-map]
  ;;(println config-map)
  (get config-map :Config))

(defn- exec-test
  [test]
  (if-let [test-case (get test :Test)]
    (println "Test is " test-case)
    "Malformed test entry, no Test section found"))

(defn- run-tests
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
           (Thread/sleep (* test-interval 1000))) tests))))

(defn- execute-tests
  [tests]
  (if (nil? (get (first tests) :Config))
    (run-tests tests)
    (run-tests (rest tests) :config (first (parse-config (first tests)))))
  (println tests))

(defn- parse
  [args]
  (if (= (count args) 1)
    (let [yaml-string (str/split (slurp (first args)) #"---")]
      ;;(println yaml-string)
      (filter (fn [x] (not (nil? x))) (map yaml/parse-string yaml-string)))
    (println "Too many parameters, program only accepts a single test file")))

(defn -main
  "Parse YAML test driver file and execute it"
  [& args]
  (if (nil? args)
    (println "Usage: mdb-test-yaml-driver <YAML file>") 
    (execute-tests (parse args))))
