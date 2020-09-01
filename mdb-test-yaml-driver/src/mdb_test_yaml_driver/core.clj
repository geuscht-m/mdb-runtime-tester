(ns mdb-test-yaml-driver.core
  (:require [tester-core.core :as tester]
            [clj-yaml.core :as yaml])
  (:gen-class))

(defn- execute-tests
  [tests]
  nil)

(defn- parse
  [args]
  (println args))

(defn -main
  "Parse YAML test driver file and execute it"
  [& args]
  (if (nil? args)
    (println "Usage: mdb-test-yaml-driver <YAML file>")
    (execute-tests (parse args))))
