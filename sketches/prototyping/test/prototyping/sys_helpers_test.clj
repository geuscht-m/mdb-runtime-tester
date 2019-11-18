(ns prototyping.sys-helpers-test
  (:require [prototyping.sys-helpers :refer :all]
            [clojure.test :refer :all]
            [clojure.pprint :refer :all]))

(deftest test-get-process-list
  (testing "Check that we can get the list of processes"
                                        ;(doall (map #(pprint %) get-process-list))))
    (pprint (get-process-list))))

(deftest test-get-os-type
  (testing "Check the result of get-os-type"
    (println (get-os-type))))
