(ns prototyping.core
  (:require [clojure.string :refer [join] ])
  (:gen-class :main true))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(load "helpers")
(load "basic-functions")
(load "test-functions")
