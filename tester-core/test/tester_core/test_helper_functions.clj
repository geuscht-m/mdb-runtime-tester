(ns tester-core.test-helper-functions
  (:require [clojure.test :refer :all]
            [tester-core.core :refer :all]
            [tester-core.test-helpers :refer :all]
            [taoensso.timbre :as timbre :refer [debug error]]))


(deftest test-is-mongodb-uri
  (testing "Check that is-mongodb-uri? returns the expected values"
    (is (is-mongodb-uri? "mongodb://localhost:27107/"))
    (is (not (is-mongodb-uri? "http://mongodb.com")))))

(deftest test-is-local-process
  (testing "Check that is-local-process? returns the expected values"
    (is (is-local-process? "mongodb://localhost"))
    (is (is-local-process? "localhost"))
    (is (not (is-local-process? "mongodb://rs1.mongodb.test")))))
