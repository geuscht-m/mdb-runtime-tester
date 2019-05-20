(in-ns 'prototyping.core)

(defn trigger-election
  "Trigger an election by issuing a stepdown command (optionally forced). Fails if URI doesn't point to a valid RS or stepdown fails"
  [rs-uri & forced]
  ())


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


(defn make-rs-degraded
  "Simulate a degraded but fully functional RS (majority of nodes still available"
  [rs-uri]
  (let [num-members  (get-num-rs-members rs-uri)
        stop-members (get-random-members rs-uri (+ (quot num-members 2) 1))]
    (map stop-mongo-process stop-members)))

(defn make-rs-read-only
  "Shut down the majority of the nodes so the RS goes read only"
  [rs-uri]
  (let [num-members   (get-num-rs-members rs-uri)
        stop-members  (get-random-members rs-uri (quot num-members 2))]
    (map stop-mongo-process stop-members)))


(defn make-shard-read-only
  "Make a single shard on a sharded cluster read only. Optionally specify RS URI for the shard to make read only"
  [cluster-uri shard-uri]
  ())

(defn make-config-servers-read-only
  "Make the config servers for a sharded cluster read only. Requires CSRS topology"
  [cluster-uri]
  ())

(defn make-random-shard-read-only
  "Similar to make-shard-read-only, but let the test code pick the shard"
  [shard-uri]
  ())

(defn make-cluster-read-only
  "Shut down the majority of nodes for each replica set making up the sharded cluster. This leaves the sharded cluster read only"
  [cluster-uri]
  ())

(defn cause-random-chaos
  "Trigger random problem for how-long, separated by a randomly varied interval of how-often"
  [how-long & how-often]
  ())

