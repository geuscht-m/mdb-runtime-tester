(in-ns 'prototyping.core)

(defn trigger-election
  "Trigger an election by issuing a stepdown command (optionally forced). Fails if URI doesn't point to a valid RS or stepdown fails"
  [rs-uri & forced]
  (stepdown-primary rs-uri))


(defn simulate-maintenance
  "Simulate maintenance/rolling mongod bounce on a replica set. Fails if the RS URI doesn't point to a valid RS"
  [rs-uri]
  (let [primary     (get-rs-primary rs-uri)
        secondaries (get-rs-secondaries rs-uri)]
    (map restart-mongo-process secondaries)
    (stepdown-primary rs-uri)
    (restart-mongo-process primary)))

(defn restart-random-rs-member
  "Restart a random member of the replica set (secondary or primary)" [rs-uri & wait-interval]
  (let [restart-this         (get-random-members rs-uri 1)]
    (restart-mongo-process restart-this wait-interval)))

(defn- partial-stop-rs
  "Note - returns the 'undo' method needed to start the members again"
  [rs-uri member-num]
  (let [stop-members (get-random-members rs-uri member-num)]
    (map stop-mongo-process stop-members)
    `(map start-mongo-process stop-members)))

(defn make-rs-degraded
  "Simulate a degraded but fully functional RS (majority of nodes still available"
  [rs-uri]
  (partial-stop-rs rs-uri (quot (get-num-rs-members rs-uri) 2)))

(defn make-rs-read-only
  "Shut down the majority of the nodes so the RS goes read only. Returns a list of stopped replica set members."
  [rs-uri]
  (partial-stop-rs rs-uri (+ (quot (get-num-rs-members rs-uri) 2) 1)))

(defn make-shard-read-only
  "Make a single shard on a sharded cluster read only. Optionally specify RS URI for the shard to make read only"
  [cluster-uri & shard-uri]
  (if (nil? shard-uri)
    (let [shard (get-random-shards cluster-uri 1)]
      (make-rs-read-only shard))
    (make-rs-read-only shard-uri)))

(defn make-config-servers-read-only
  "Make the config servers for a sharded cluster read only. Requires CSRS topology"
  [cluster-uri]
  (let [config-server-uri (get-config-servers-uri cluster-uri)]
    (make-rs-read-only config-server-uri)))

(defn make-random-shard-read-only
  "Similar to make-shard-read-only, but let the test code pick the shard"
  [cluster-uri]
  (let [shard (get-random-shards cluster-uri 1)]
    (make-rs-read-only shard)))

(defn make-cluster-read-only
  "Shut down the majority of nodes for each replica set making up the sharded cluster. This leaves the sharded cluster read only"
  [cluster-uri]
  (let [shard-uris (get-shard-uris cluster-uri)]
    (map make-shard-read-only shard-uris)))

(defn cause-random-chaos
  "Trigger random problem for how-long, separated by a randomly varied interval of how-often"
  [how-long & how-often]
  ())

