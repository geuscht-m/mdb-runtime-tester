(ns mdb-test-yaml-driver.core
  (:require [mdb-test-yaml-driver.impl :as impl])
  (:gen-class))


(defn -main
  "Parse YAML test driver file and execute it"
  [& args]
  (if (nil? args)
    (println "Usage: mdb-test-yaml-driver <YAML file>") 
    (impl/execute-tests (impl/parse args))))
