;; Helper functions for Mongodb discovery mechanisms

(in-ns 'prototyping.core)
(require '[clojure.string :as str]
         '[monger.core :as mg]
         '[monger.command :as mcmd]
         '[monger.conversion :as mcv])

;;
;; A bunch of mongodb driver interface helpers
;;
(defn- run-listshards
  "Returns the output of the mongodb listShards admin command"
  [uri]
  )

(defn- run-replset-get-config
  "Returns the output of mongodb's replSetGetConfig admin command"
  [uri]
  )

(defn- run-replset-get-status
  "Returns the result of Mongodb's replSetGetStatus admin command"
  [uri]
  (let [conn (mg/connect uri)]
    (mcv/from-db-object (mcmd/admin-command conn { :replSetGetStatus 1 }) true)))

(defn- run-get-shard-map
  "Returns the output of MongoDB's getShardMap admin command"
  [uri]
  (let [conn (mg/connect uri)]
    (mcv/from-db-object (mcmd/admin-command conn { :getShardMap 1 }) true)))

(defn- run-replset-stepdown
  [uri]
  )

(defn- run-shutdown-command
  "Run the shutdown command on a remote or local mongod/s"
  [uri & force]
  )

(defn- run-remote-ssh-command
  [cmd]
  )

;; Replica set topology functions to
;; - Retrieve the connection URI for the primary/secondaries
;; - Get the number of nodes in an RS
(defn get-rs-primary
  "Retrieve the primary from a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri]
  (first (filter #(= (get % :stateStr) "PRIMARY") (get (run-replset-get-status uri) :members))))

(defn get-rs-secondaries
  "Retrieve a list of secondaries for a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri]
  (let [rs-state (run-replset-get-status uri)]
    (filter #(= (get % :stateStr) "SECONDARY") (get rs-state :members))))

(defn get-num-rs-members
  "Retrieve the number of members in a replica set referenced by its uri"
  [uri]
  (count (run-replset-get-status uri)))


(defn is-local-process?
  "Check if the mongo process referenced by the URI is local or not"
  [uri]
  true)

(defn- get-process-type
  [uri]
  (let [conn (mg/connect uri)]
    (mcv/from-db-object (mcmd/server-status (mg/get-db conn "admin")) true)))
  

(defn is-mongod-process?
  "Check if the process referenced by the URI is a mongod process"
  [uri]
  (= (get (get-process-type uri) :process) "mongod"))

(defn is-mongos-process?
  "Check if the process referenced by the URI is a mongos process"
  [uri]
  (= (get (get-process-type uri) :process) "mongos"))

(defn start-local-mongo-process [uri]
  ())

(defn start-remote-mongo-process [uri]
  (run-remote-ssh-command uri))

(defn stop-mongod-process [uri]
  (run-shutdown-command uri))

(defn stop-mongos-process [uri]
  (run-shutdown-command uri))


(defn send-mongo-rs-stepdown
  "Sends stepdown to the mongod referenced by the URI"
  [uri]
  (run-replset-stepdown uri))
  
(defn get-random-members
  "Returns a list of n random replica set members from the replica set referenced by uri"
  [uri n]
  (let [rs-members (get :members (run-replset-get-status uri))]
    (take n (shuffle rs-members))))

(defn get-random-shards
  "Returns a list of n random shards from the sharded cluster referenced by the uri"
  [uri n]
  (let [shards (get :shards (run-listshards uri))]
    (take n (shuffle shards))))

(defn get-config-servers-uri
  "Given a sharded cluster, returns the URI needed to connect to the config servers"
  [cluster-uri]
  (let [shard-map (run-get-shard-map cluster-uri)]
    (println shard-map)
    (str/split (get (get shard-map :map) :config) #",")))

(defn get-shard-uris
  "Retrieve the URIs for the individual shards that make up the sharded cluster.
   cluster-uri _must_ point to the mongos for correct discovery."
  [cluster-uri]
  (let [shard-configs (run-listshards cluster-uri)]
    (map (get :host) (get :shards shard-configs))))

(defn not-expired?
  "Check if the current time is still within the expected interval"
  [end-time]
  )

(defn is-sharded-cluster?
  "Check if the cluster specified by the URI is a sharded cluster or a replica set"
  [uri]
  (empty? (get-shard-uris uri)))

(defn undo-operation
  "On functions that return a closure, execute the closure"
  [returned-closure]
  )
