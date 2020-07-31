(ns tester-core.sys-helpers
  (:require [clojure.java.shell :refer [sh]]))

(defn get-os-type
  "Retrieve the OS of the current system"
  []
  (System/getProperty "os.name"))

(defn- parse-ps-output
  "Parse a line of 'ps ax' output"
  [ps-line]
  (let [parsed-line (re-find (re-matcher #"^(\d+)\s+(.+)" (clojure.string/triml ps-line)))]
    { :pid (nth parsed-line 1) :command-line (nth parsed-line 2) }))

(defn- get-process-list-windows
  []
  )

(defn- get-process-list-bsd
  "Uses the BSD syntax version of ps to retrieve a list of running processes.
   BSD syntax is used as it works on Mac OS and Linux (plus very likely on *BSD)."
  []
  (let [proc-list (sh "ps" "ax" "-o" "pid=" "-o" "command=")]
    (doall (map #(parse-ps-output %) (clojure.string/split-lines (get proc-list :out))))))
  
   
;; NOTE - this code can be improved by using the ProcessHandle API on Java 9+
;;        As it is written, this code also works with Java 8.
(defn get-process-list
  []
  (let [os (get-os-type)]
    (cond
        (= os "Linux")    (get-process-list-bsd)
        (= os "Mac OS X") (get-process-list-bsd)
        (= os "Windows")  (get-process-list-windows))))
