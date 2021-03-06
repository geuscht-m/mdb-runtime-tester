(ns tester-core.test-helpers
  (:require [tester-core.core :refer :all]
            [tester-core.sys-helpers :as sys :refer :all]
            [tester-core.mini-driver :as md :refer :all]
            [clj-ssh.ssh :as ssh :refer :all]
            [clojure.set :refer :all]
            [taoensso.timbre :as timbre :refer :all])
  (:import  [com.mongodb ReadPreference]))

(defn setup-logging-fixture
  [f]
  (timbre/debug "setting up logging")
  (timbre/merge-config! {:min-level `[[#{"org.mongodb.*"} :error] [#{"*"} :warn]]})
  (f))

(defn setup-logging-fixture-debug
  [f]
  (timbre/debug "setting up logging")
  (timbre/merge-config! {:min-level `[[#{"org.mongodb.*"} :error] [#{"clj-ssh.*"} :warn] [#{"*"} :debug]]})
  (f))


(defn available-mongods
  "Try to create a list of available mongods on the current machine"
  []
  (filter (fn [entry] (re-find #"^mongod\s+" (get entry :command-line))) (get-process-list)))

(defn num-running-mongo-processes
  "Figure out how many mongodb process (mongos or mongod) are currently running"
  ([]
   (let [processes (get-process-list)
         running   (filter (fn [entry] (re-find #"^mongo[ds]\s+" (get entry :command-line))) processes)]
     ;;(println "currently running mongo processes " running)
     (count running)))
  ([server-list]
   (let [processes (apply clojure.set/union (get-process-list server-list))]
     ;; (println "type of processes " (type processes))
     ;; (println "num-running-mongo-processes - processes are " processes)
     (let [running   (filter (fn [entry] (re-find #"^mongo[ds]\s+" (get entry :command-line))) processes)]
       ;;(println "Running mongo processes are " running)
       (count running)))))

(defn wait-mongo-shutdown
  "Wait until we have no further MongoDB processes running"
  ([max-retries]
   (let [retries (atom 0)]
     (while (and (> (num-running-mongo-processes) 0) (< @retries max-retries))
       (Thread/sleep 500)
       (reset! retries (inc @retries)))
     (if (>= @retries max-retries)
       (println "Unable to shut down remaining mongo processes, aborting"))))
  ([server-list max-retries]
   (let [retries (atom 0)]
     (while (and (> (num-running-mongo-processes server-list) 0) (< @retries max-retries))
       (do ;;(println "Waiting for remote mongo process to shut down, attempt " @retries)
         (Thread/sleep 500)
         (reset! retries (inc @retries))))
     (if (>= @retries max-retries)
       (println "Unable to shut down remaining mongod processes")))))

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
  [rs-uri & { :keys [ ^String user ^String pwd ssl root-ca client-cert auth-mechanism ] :or { user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil } }]
   (let [rs-status (get (run-replset-get-status rs-uri :user user :pwd pwd :ssl ssl :root-ca root-ca :read-preference (ReadPreference/primaryPreferred) :client-cert client-cert :auth-mechanism auth-mechanism) :members)
         degraded  (map #(= (get % :stateStr) "(not reachable/healthy)") rs-status)]
     ;;(println "\nReplica set status is " rs-status)
     ;;(println "degraded is " (some identity degraded) "\n")
     (some true? degraded)))
     
(defn replicaset-ready?
  "Check if the replica set at URI is ready (has a primary and the requisite number of total active nodes"
  [rs-uri num-nodes & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :or { user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil } }]
  (let [conn (md/mdb-connect rs-uri :user user :pwd pwd :ssl ssl :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism)
        num-active (num-active-rs-members conn)
        primary    (get-rs-primary conn)]
    (md/mdb-disconnect conn)
    (and (= num-active num-nodes)
         (some? primary))))
  ;; (and (= (num-active-rs-members rs-uri :user user :pwd pwd :ssl ssl :root-ca root-ca) num-nodes)
  ;;      (some? (get-rs-primary rs-uri :user user :pwd pwd :ssl ssl :root-ca root-ca))))

(defn wait-test-rs-ready
  "Waits until the replica set is ready for testing so we don't
   have to play with timeouts all the time"
  [rs-uri num-mem max-retries & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :or { user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil} }]
   (let [retries (atom 0)]
     (while (and (not (replicaset-ready? rs-uri num-mem :user user :pwd pwd :ssl ssl :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism)) (< @retries max-retries))
       ;;(println "Waiting for test environment at " rs-uri " with user " user ", pwd " pwd " and root-ca " root-ca " to get ready")
       (reset! retries (inc @retries))
       (Thread/sleep 1100)
       )
     ;;(Thread/sleep 750)
     (< @retries max-retries)))

(defn replica-set-read-only?
  "Check if the replica set is read only by checking if it has no primary)"
  [rs-uri & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :or { user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil } } ]
  (let [mongo-uri   (make-mongo-uri rs-uri)
        ssl-enabled (or ssl (.contains mongo-uri "ssl=true"))
        primary     (get-rs-primary mongo-uri :user user :pwd pwd :ssl ssl-enabled :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism)]
    (nil? primary)))
  

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
;; (defn- run-remote-ssh-command
;;   "Execute a command described by cmdline on the remote server 'server'"
;;   [server cmdline]
;;   ;;(println "\nAttempting to run ssh command " cmdline "\n")
;;   (let [agent   (ssh/ssh-agent {})
;;         session (ssh/session agent server {:strict-host-key-checking :no})]
;;     (ssh/with-connection session
;;       (let [result (ssh/ssh session { :cmd cmdline })]
;;         result))))

(defn ssh-apply-command-to-rs-servers
  [cmd servers]
  (timbre/debug "ssh-apply-command-to-rs-servers: trying to apply command " cmd " of type " (type cmd) " to servers " servers)
  (doall (map #(sys/run-remote-ssh-command % cmd) servers)))

