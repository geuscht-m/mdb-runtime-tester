(ns tester-core.sys-helpers
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str :refer [join trim-newline]]
            [clj-ssh.ssh :as ssh :refer :all]))

;;
;; SSH-based test helpers
;;
(defn run-remote-ssh-command
  "Execute a command described by cmdline on the remote server 'server'"
  [server cmdline]
  ;;(println "\nAttempting to run ssh command " cmdline "\n")
  (let [agent   (ssh/ssh-agent {})
        session (ssh/session agent server {:strict-host-key-checking :no})]
    (ssh/with-connection session
      (let [result (ssh/ssh session { :cmd cmdline })]
        result))))


(defn get-os-type
  "Retrieve the OS of the current system"
  ([]
   (System/getProperty "os.name"))
  ([hostname]
  (let [agent   (ssh/ssh-agent {})
        session (ssh/session agent hostname {:strict-host-key-checking :no})]
    (ssh/with-connection session
      (let [result (ssh/ssh session { :cmd "uname" })]
        (str/trim-newline (:out result)))))))
   

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
  ([]
   (let [proc-list (sh "ps" "ax" "-o" "pid=" "-o" "command=")]
     (doall (map #(parse-ps-output %) (clojure.string/split-lines (get proc-list :out))))))
  ([server]
   (let [proc-list (run-remote-ssh-command server "ps ax -o pid= -o command=")]
     (doall (map #(parse-ps-output %) (clojure.string/split-lines (get proc-list :out)))))))
  
   
;; NOTE - this code can be improved by using the ProcessHandle API on Java 9+
;;        As it is written, this code also works with Java 8.
(defn get-process-list
  ([]
   (let [os (get-os-type)]
     (cond
       (= os "Linux")    (get-process-list-bsd)
       (= os "Mac OS X") (get-process-list-bsd)
       (= os "Windows")  (get-process-list-windows))))
  ([server-list]
   (doall (map #(get-process-list-bsd %) server-list))))

(defn- check-if-service-linux
  [hostname pid]
  (let [cmdline (str/join ["systemctl status " (str pid) " | grep '\\.service'"])]
    (if (= hostname "localhost")
      nil
      (let [agent   (ssh/ssh-agent {})
            session (ssh/session agent hostname {:strict-host-key-checking :no})]
        (ssh/with-connection session
          (= (:exit (ssh/ssh session { :cmd cmdline })) 0))))))
  

(defn check-if-service
  "Checks if a process was started via a service. Works on most supported Linux versions
   with the exception of Amazon Linux 1"
  [hostname pid]
  (let [os (get-os-type hostname)]
    (cond
      (= os "Linux")  (check-if-service-linux hostname pid)
      :else nil)))
