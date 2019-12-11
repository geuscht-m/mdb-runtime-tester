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

(defn replica-set-read-only?
  "Check if the replica set is read only (ie, has no primary)"
  [rs-uri]
  (nil? (get-rs-primary (make-mongo-uri rs-uri))))
  

(defn shards-read-only?
  "Check if all shards of a sharded cluster are read only."
  [cluster-uri]
  (println "\nGetting shard uris for cluster uri " cluster-uri "\n")
  (let [shards (if (= (type cluster-uri) String) (get-shard-uris cluster-uri) cluster-uri)]
      (println "\nShards: " shards "\n")
      (and (map #(replica-set-read-only? (convert-shard-uri-to-mongo-uri %)) shards))))
