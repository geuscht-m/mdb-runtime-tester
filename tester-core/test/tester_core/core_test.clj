(ns tester-core.core-test
  (:require [clojure.test :refer :all]
            [tester-core.core :refer :all]
            [taoensso.timbre :as timbre]))

;; (timbre/merge-config!
;;   {:level :warn
;;    :ns-blacklist [] #_["org.mongodb.*"]})

