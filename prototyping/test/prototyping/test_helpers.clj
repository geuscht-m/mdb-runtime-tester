(ns prototyping.test-helpers
  (:require [prototyping.core :refer :all]
            [prototyping.sys-helpers :refer :all]))

(defn available-mongods
  "Try to create a list of available mongods on the current machine"
  []
  (filter (fn [entry] (re-find #"^mongod\s+" (get entry :command-line))) (get-process-list)))

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
    (println uri-parts)
    (if (not (nil? uri-parts))
      (make-mongo-uri (nth uri-parts 1))
      nil)))

(defn replicaset-degraded?
  "Check if the replica set has at least one node that is in (not reachable/healthy) state"
  ([rs-uri]
   (let [rs-status (get (run-replset-get-status rs-uri) :members)
         degraded  (doall (map #(= (get % :stateStr) "(not reachable/healthy)") rs-status))]
     (some identity degraded)))
  ([rs-uri ^String user ^String pw]
   (let [rs-status (get (run-replset-get-status rs-uri user pw) :members)
         degraded  (doall (map #(= (get % :stateStr) "(not reachable/healthy)") rs-status))]
     ;;(println "\nReplica set status is " rs-status)
     ;;(println "degraded is " (some identity degraded) "\n")
     (some identity degraded))))
     


(defn replica-set-read-only?
  "Check if the replica set is read only (ie, has no primary)"
  ([rs-uri]
   (let [primary (get-rs-primary (make-mongo-uri rs-uri))
         replset (run-replset-get-status (make-mongo-uri rs-uri))]
     ;;(println "\nget-rs-primary returned " primary "\n")
     ;;(println "\nget-replset-status returned " (get replset :members) "\n")
     (nil? primary)))
  ([rs-uri ^String user ^String pw]
   (let [mongo-uri   (make-mongo-uri rs-uri)
         primary     (get-rs-primary mongo-uri user pw)]
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
    (and (map #(replicaset-degraded? (convert-shard-uri-to-mongo-uri %)) shard-list))))

(defn shards-read-only?
  "Check if all shards of a sharded cluster are read only."
  [cluster-uri]
  ;;(println "\nGetting shard uris for cluster uri " cluster-uri "\n")
  (let [shards (if (= (type cluster-uri) String) (get-shard-uris cluster-uri) cluster-uri)]
      ;;(println "\nShards: " shards "\n")
      (and (map #(replica-set-read-only? (convert-shard-uri-to-mongo-uri %)) shards))))
