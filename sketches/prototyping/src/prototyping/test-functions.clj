(in-ns 'prototyping.core)

(defn trigger-election
  "Trigger an election by issuing a stepdown command (optionally forced). Fails if URI doesn't point to a valid RS or stepdown fails"
  [rs-uri & forced]
  (println "\nAttempting to trigger election in RS " rs-uri)
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
  "Internal helper function to stop _member-num_ members of a replica set.
   Note - returns the 'undo' method needed to start the members again"
  [rs-uri member-num]
  (let [stop-members (doall (map #(get % :name) (get-random-members rs-uri member-num)))]
    (println "\n\nAttempting to stop mongod servers " stop-members)
    (doall (map stop-mongo-process stop-members))
    (fn [] (map start-mongo-process stop-members))))

(defn make-rs-degraded
  "Simulate a degraded but fully functional RS (majority of nodes still available"
  [rs-uri]
  (let [num-members (get-num-rs-members rs-uri)
        stop-rs-num (quot num-members 2)]
    (println "Stopping n servers with n out of m servers " stop-rs-num num-members)
    (partial-stop-rs rs-uri stop-rs-num)))

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
  "Trigger random problem for how-long on any of the clusters listed in cluster-list, separated by a randomly varied interval of how-often"
  [cluster-list how-long max-wait & how-often]
  (let [rs-function-list (trigger-election make-rs-degraded make-rs-read-only)
        shard-function-list (make-shard-read-only make-config-servers-read-only make-random-shard-read-only make-cluster-read-only)]
    (while (not-expired? how-long)
      (if (is-sharded-cluster? (rand-nth cluster-list))
        (undo-operation (rand-nth shard-function-list) (rand max-wait))
        (undo-operation (rand-nth rs-function-list) (rand max-wait))))))


