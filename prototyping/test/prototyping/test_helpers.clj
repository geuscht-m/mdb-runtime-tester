(ns prototyping.test-helpers
  (:require [prototyping.core :refer :all]
            [prototyping.sys-helpers :refer :all]
            [clj-ssh.ssh :as ssh :refer :all])
  (:import  [com.mongodb ReadPreference]))


(defn available-mongods
  "Try to create a list of available mongods on the current machine"
  []
  (filter (fn [entry] (re-find #"^mongod\s+" (get entry :command-line))) (get-process-list)))

(defn num-running-mongo-processes
  "Figure out how many mongodb process (mongos or mongod) are currently running"
  []
  (let [processes (get-process-list)
        running   (filter (fn [entry] (re-find #"^mongo[ds]\s+" (get entry :command-line))) processes)]
    ;;(println "currently running mongo processes " running)
    (count running)))

(defn mongodb-port-list
  "Given a process list, retrieve the list of mongod/mongos port numbers of active
   processes"
  [process-list]
  ;;(println process-list)
  (doall (map #(nth (re-matches #".+--port\s+(\d+).*" (get % :command-line)) 1) process-list)))

(defn- convert-shard-uri-to-mongo-uri
  "Take the MongoDB shard URI format and turn it into the format that
   MongoDB understands"
  [shard-uri]
  (let [uri-parts (re-matches #"shard\d+/(.*)" shard-uri)]
    ;;(println "shard uri conversion - uri parts are " uri-parts)
    (if (not (nil? uri-parts))
      (make-mongo-uri (nth uri-parts 1))
      nil)))

(defn replicaset-degraded?
  "Check if the replica set has at least one node that is in (not reachable/healthy) state"
  ([rs-uri]
   ;;(println "Checking if replica set at " rs-uri " is degraded or not")
   (let [rs-status (get (run-replset-get-status rs-uri) :members)
         ;;degraded  (doall (map #(= (get % :stateStr) "(not reachable/healthy)") rs-status))]
         degraded  (map #(= (get % :stateStr) "(not reachable/healthy)") rs-status)]
     ;;(println "Degraded replica set members " degraded "\n")
     (some true? degraded)))
  ([rs-uri ^String user ^String pw]
   (let [rs-status (get (run-replset-get-status rs-uri :user user :password pw) :members)
         degraded  (map #(= (get % :stateStr) "(not reachable/healthy)") rs-status)]
     ;;(println "\nReplica set status is " rs-status)
     ;;(println "degraded is " (some identity degraded) "\n")
     (some true? degraded))))
     
(defn replicaset-ready?
  "Check if the replica set at URI is ready (has a primary and the requisite number of total active nodes"
  [rs-uri num-nodes & { :keys [ user pw ssl ] :or { user nil pw nil ssl false } }]
  (and (= (num-active-rs-members rs-uri :user user :pwd pw :ssl ssl) num-nodes) (some? (get-rs-primary rs-uri :user user :pw pw :ssl ssl))))

(defn wait-test-rs-ready
  "Waits until the replica set is ready for testing so we don't
   have to play with timeouts all the time"
  ;; ([rs-uri num-mem max-retries]
  ;;  (let [retries (atom 0)]
  ;;    (while (and (not (replicaset-ready? rs-uri num-mem)) (< @retries max-retries))
  ;;      (reset! retries (inc @retries))
  ;;      (Thread/sleep 1100)
  ;;      )
  ;;    (< @retries max-retries)))
  ;; ([rs-uri num-mem user pw max-retries]
  ;;  (let [retries (atom 0)]
  ;;    (while (and (not (replicaset-ready? rs-uri num-mem user pw)) (< @retries max-retries))
  ;;      (reset! retries (inc @retries))
  ;;      (Thread/sleep 1100)
  ;;      )
  ;;    (< @retries max-retries)))
  [rs-uri num-mem max-retries & { :keys [ user pw ssl ] :or { user nil pw nil ssl false } }]
   (let [retries (atom 0)]
     (while (and (not (replicaset-ready? rs-uri num-mem :user user :pw pw :ssl ssl)) (< @retries max-retries))
       (reset! retries (inc @retries))
       (Thread/sleep 1100)
       )
     (< @retries max-retries)))

(defn replica-set-read-only?
  "Check if the replica set is read only (ie, has no primary)"
  ([rs-uri]
   ;;(println "Trying to get primary for URI " rs-uri)
   (let [;;primary (get-rs-primary rs-uri (ReadPreference/primaryPreferred))
         replset (run-replset-get-status rs-uri :read-preference (ReadPreference/primaryPreferred))
         primary (first (filter #(= (get % :stateStr) "PRIMARY") (get replset :members)))]
     ;;(println "\nget-rs-primary for replica set " rs-uri " returned " primary "\n")
     ;;(println "\nget-replset-status for replica set " rs-uri " returned " (get replset :members) "\n")
     ;;(println "\nget-replset-status for replica set " rs-uri " returned " replset "\n")
     (nil? primary)))
  ([rs-uri ^String user ^String pw]
   (let [mongo-uri   (make-mongo-uri rs-uri)
         primary     (get-rs-primary mongo-uri :user user :pw pw)]
     ;;replset     (run-replset-get-status mongo-uri user pw)]
     ;;(println "\nget-rs-primary returned " primary "\n")
     ;;(println "\nget-replset-status returned " (get replset :members) "\n")
     (nil? primary))))
  

(defn shard-degraded?
  ([shard-uri]
   ;;(println "\n" (get (run-replset-get-status (make-mongo-uri shard-uri)) :members)))
   (replicaset-degraded? shard-uri))
  ([shard-uri ^String user ^String pw]
   (replicaset-degraded? shard-uri user pw)))

(defn shard-read-only?
  "Wrapper around replica-set-read-only? - does the same, present
   for more readable code"
  ([shard-uri]
   (replica-set-read-only? shard-uri))
  ([shard-uri ^String user ^String pw]
   (replica-set-read-only? shard-uri user pw)))

(defn cluster-degraded?
  "Check that all shards on a cluster are in degraded state"
  [cluster-uri]
  (let [shard-list (get-shard-uris cluster-uri)]
    ;;(println "Check shard list for cluster degradation at uri " cluster-uri)
    ;;(println "Shard list is " shard-list)
    (every? true? (map #(replicaset-degraded? %) shard-list))))

(defn shards-read-only?
  "Check if all shards of a sharded cluster are read only."
  [uri]
  (if (seq? uri)
    (every? true? (map replica-set-read-only? uri))
    (let [shards (get-shard-uris uri)]
      (println "\nShards: " shards "\n")
      (every? true? (doall (map #(replica-set-read-only? (convert-shard-uri-to-mongo-uri %)) shards))))))

;;
;; SSH-based test helpers
;;
(defn- run-remote-ssh-command
  "Execute a command described by cmdline on the remote server 'server'"
  [server cmdline]
  ;;(println "\nAttempting to run ssh command " cmdline "\n")
  (let [agent   (ssh/ssh-agent {})
        session (ssh/session agent server {:strict-host-key-checking :no})]
    (ssh/with-connection session
      (let [result (ssh/ssh session { :cmd cmdline })]
        result))))

(defn ssh-apply-command-to-rs-servers
  [cmd servers]
  (doall (map #(run-remote-ssh-command % cmd) servers)))
