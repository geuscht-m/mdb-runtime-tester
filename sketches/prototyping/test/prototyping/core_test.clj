(ns prototyping.core-test
  (:require [clojure.test :refer :all]
            [prototyping.core :refer :all]
            [taoensso.timbre :as timbre]))

(timbre/merge-config!
  {:level :warn
   :ns-blacklist [] #_["org.mongodb.*"]})

