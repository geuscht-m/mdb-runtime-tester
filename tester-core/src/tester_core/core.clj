(ns tester-core.core
  (:require   [taoensso.timbre :as timbre :ref [log trace debug info warn error fatel report logf tracef debugf infof warnf errorf fatalf reportf spy getenv]])
  (:gen-class :main true))

(load "helpers")
(load "basic_functions")
(load "test_functions")
