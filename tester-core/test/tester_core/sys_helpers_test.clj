(ns tester-core.sys-helpers-test
  (:require [tester-core.sys-helpers :refer :all]
            [clojure.test :refer :all]
            [clojure.pprint :refer :all]))

(deftest test-get-process-list
  (testing "Check that we can get the list of processes"
                                        ;(doall (map #(pprint %) get-process-list))))
    (let [proc-list (get-process-list)]      
      (not (nil? proc-list))
      (is (> (count proc-list) 1))
      (is (= (compare (keys (first proc-list)) (:pid :command-line))))      
    )))

(deftest test-get-os-type
  (testing "Check the result of get-os-type"
    (let [os-type (get-os-type)]
      (is (or (= os-type "Linux") (= os-type "Mac OS X") (= os-type "Windows"))))))
