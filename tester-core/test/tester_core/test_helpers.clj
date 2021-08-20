(ns tester-core.test-helpers
  (:require [tester-core.core :refer :all]
            [tester-core.sys-helpers :as sys :refer :all]
            [tester-core.mini-driver :as md :refer :all]
;;            [clj-ssh.ssh :as ssh :refer :all]
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
     (timbre/trace "currently running mongo processes " running)
     (count running)))
  ([server-list]
   (let [processes (apply clojure.set/union (get-process-list server-list))]
     ;;(timbre/trace "type of processes " (type processes) ", num-running-mongo-processes - processes are " processes)
     (let [running   (filter (fn [entry] (re-find #"^mongo[ds]\s+" (get entry :command-line))) processes)]
       ;;(timbre/trace "Running mongo processes are " running)
       (count running)))))

(defn wait-mongo-shutdown
  "Wait until we have no further MongoDB processes running"
  ([max-retries]
   (let [retries (atom 0)]
     (while (and (> (num-running-mongo-processes) 0) (< @retries max-retries))
       (Thread/sleep 750)
       (reset! retries (inc @retries)))
     (if (>= @retries max-retries)
       (timbre/warn "Unable to shut down remaining mongo processes, aborting"))))
  ([server-list max-retries]
   (let [retries (atom 0)]
     (while (and (> (num-running-mongo-processes server-list) 0) (< @retries max-retries))
       (do
         (timbre/debug "Waiting for remote mongo process to shut down, attempt " @retries)
         (Thread/sleep 500)
         (reset! retries (inc @retries))))
     (if (>= @retries max-retries)
       (timbre/warn "Unable to shut down remaining mongod processes")))))

(defn mongodb-port-list
  "Given a process list, retrieve the list of mongod/mongos port numbers of active
   processes"
  [process-list]
  (doall (map #(nth (re-matches #".+--port\s+(\d+).*" (get % :command-line)) 1) process-list)))

(defn- convert-shard-uri-to-mongo-uri
  "Take the MongoDB shard URI format and turn it into the format that
   MongoDB understands"
  [shard-uri]
  (let [uri-parts (re-matches #"shard\d+/(.*)" shard-uri)]
    (timbre/trace "shard uri conversion - uri parts are " uri-parts)
    (if (not (nil? uri-parts))
      (make-mongo-uri (nth uri-parts 1))
      nil)))

(defn replicaset-degraded?
  "Check if the replica set has at least one node that is in (not reachable/healthy) state"
  [rs-uri & { :keys [ ^String user ^String pwd ssl root-ca client-cert auth-mechanism ] :as opts }]
   (let [rs-status (:members (apply run-replset-get-status rs-uri :read-preference (ReadPreference/primaryPreferred) (mapcat identity opts)))
         degraded  (map #(= (get % :stateStr) "(not reachable/healthy)") rs-status)]
     (timbre/trace "Replica set status is " rs-status ", degraded is " (some identity degraded))
     (some true? degraded)))
     
(defn replicaset-ready?
  "Check if the replica set at URI is ready (has a primary and the requisite number of total active nodes"
  [rs-uri num-nodes & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :as opts}]
  (try
    (let [conn       (apply md/mdb-connect rs-uri (mapcat identity opts))
          num-active (apply num-active-rs-members conn (mapcat identity opts))
          primary    (apply get-rs-primary conn (mapcat identity opts))]
      (md/mdb-disconnect conn)
      (and (= num-active num-nodes)
           (some? primary)))
    (catch com.mongodb.MongoTimeoutException e false)))

(defn wait-test-rs-ready
  "Waits until the replica set is ready for testing so we don't
   have to play with timeouts all the time"
  [rs-uri num-mem max-retries & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :as opts }]
   (let [retries (atom 0)]
     (while (and (not (apply replicaset-ready? rs-uri num-mem (mapcat identity opts))) (< @retries max-retries))
       (reset! retries (inc @retries))
       (Thread/sleep 1100)
       )
     ;;(Thread/sleep 750)
     (< @retries max-retries)))

(defn replica-set-read-only?
  "Check if the replica set is read only by checking if it has no primary)"
  [rs-uri & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :as opts } ]
  (let [mongo-uri   (make-mongo-uri rs-uri)
        primary     (apply get-rs-primary mongo-uri (mapcat identity opts))]
    (nil? primary)))
  

(defn shard-degraded?
  ([shard-uri]
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
    (timbre/trace "Check shard list " shard-list " for cluster degradation at uri " cluster-uri)
    (every? true? (map #(replicaset-degraded? %) shard-list))))

(defn shards-read-only?
  "Check if all shards of a sharded cluster are read only."
  [uri]
  (if (seq? uri)
    (every? true? (map replica-set-read-only? uri))
    (let [shards (get-shard-uris uri)]
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

