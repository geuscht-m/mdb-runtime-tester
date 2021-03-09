(in-ns 'tester-core.core)

(defn trigger-election
  "Trigger an election by issuing a stepdown command (optionally forced). Fails if URI doesn't point to a valid RS or stepdown fails"
  [rs-uri & { :keys [forced user pwd ssl root-ca client-cert auth-mechanism] :or {forced false user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil}}]
  (stepdown-primary rs-uri :user user :pwd pwd :ssl ssl :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism))

(defn simulate-maintenance
  "Simulate maintenance/rolling mongod bounce on a replica set. Fails if the RS URI doesn't point to a valid RS"
  [rs-uri & { :keys [ user pwd ssl root-ca client-cert auth-mechanism] :or { user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil} }]
  (let [conn        (md/mdb-connect rs-uri :user user :pwd pwd :ssl ssl :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism)
        primary     (get (get-rs-primary conn) :name)
        secondaries (doall (map #(get % :name) (get-rs-secondaries conn)))]
    (md/mdb-disconnect conn)
    ;;(println "Primary is " primary ", secondaries are " secondaries)
    (doall (map #(restart-mongo-process (make-mongo-uri %) :user user :pwd pwd :ssl ssl :root-ca root-ca) secondaries))
    ;;(stepdown-primary rs-uri :user user :password pw)
    (stepdown-primary (make-mongo-uri primary) :user user :pwd pwd :ssl ssl :root-ca root-ca)
    (restart-mongo-process (make-mongo-uri primary) :user user :pwd pwd :ssl ssl :root-ca root-ca)))

(defn restart-random-rs-member
  "Restart a random member of the replica set (secondary or primary)"
  [rs-uri & wait-interval]
  (let [restart-this         (get-random-members rs-uri 1)]
    (restart-mongo-process restart-this wait-interval)))

(defn- partial-stop-rs
  "Internal helper function to stop _member-num_ members of a replica set.
   Note - returns the 'undo' method needed to start the members again."
  [rs-uri member-num & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :or { user nil pwd nil ssl false root-ca nil client-cert nil auth-mechanism nil } }]
  (let [ssl-enabled  (or ssl (.contains rs-uri "ssl=true"))
        stop-members (doall (map #(make-mongo-uri (get % :name)) (get-random-members rs-uri member-num :user user :pwd pwd :ssl ssl-enabled :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism)))
        restart-info (into () (doall (map #(stop-mongo-process % :user user :pwd pwd :ssl ssl-enabled :root-ca root-ca :client-cert client-cert :auth-mechanism auth-mechanism) stop-members)))]
    (timbre/debug "partial-stop-rs: restart info is " restart-info)
    ;;(flush)
    (fn [] (do (timbre/debug "Attempting to execute restart function with info " restart-info)
               (if (seq? restart-info)
                 (doall (map (fn[info]
                               (do (timbre/debug "Calling restart function with list of  parameters " info)
                                   (start-mongo-process (get info :uri) (get info :cmd-line)))) restart-info))
                 (do (timbre/debug "Calling restart function with parameters " restart-info)
                     (start-mongo-process (get restart-info :uri) (get restart-info :cmd-line))))))))

(defn make-rs-degraded
  "Simulate a degraded but fully functional RS (majority of nodes still available"
  [rs-uri & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :as opts }]
  ;;(println "make-rs-degraded called with URI " rs-uri " and user " user ", password " pwd ", root-ca " root-ca)
  (let [num-members  (apply get-num-rs-members rs-uri (mapcat identity opts))
         stop-rs-num (quot num-members 2)]
    (apply partial-stop-rs rs-uri stop-rs-num (mapcat identity opts))))

(defn make-rs-read-only
  "Shut down the majority of the nodes so the RS goes read only. Returns a list of stopped replica set members."
  [rs-uri & { :keys [ user pwd ssl root-ca client-cert auth-mechanism ] :as opts }]
  ;;(println "\nMaking replica set read only " rs-uri "\n")
  (apply partial-stop-rs rs-uri (+ (quot (apply get-num-rs-members rs-uri (mapcat identity opts)) 2) 1) (mapcat identity opts)))

(defn make-shard-degraded
  "Simulate a single degraded shard on a sharded cluster"
  [shard-uri]
  (make-rs-degraded shard-uri))

(defn make-shard-read-only
  "Make a single, defined shard on a sharded cluster read only.
   If you want to make a random shard read only, use the function make-random-shard-read-only"
  [shard-uri]
  (make-rs-read-only shard-uri))

(defn make-random-shard-read-only
  "Similar to make-shard-read-only, but let the test code pick the shard"
  [cluster-uri]
  (let [shard (get-random-shards cluster-uri 1)]
    (make-rs-read-only shard)))

(defn make-config-servers-read-only
  "Make the config servers for a sharded cluster read only. Requires CSRS topology"
  [cluster-uri]
  (let [config-server-uri (get-config-servers-uri cluster-uri)]
    (make-rs-read-only config-server-uri)))

(defn make-sharded-cluster-degraded
  "Shut down the minority of nodes for each replica set on a sharded cluster"
  [cluster-uri]
  (let [shard-uris (get-shard-uris cluster-uri)]
    (doall (map #(make-shard-degraded (make-mongo-uri %)) shard-uris))))

(defn make-sharded-cluster-read-only
  "Shut down the majority of nodes for each replica set making up the sharded cluster. This leaves the sharded cluster read only"
  [cluster-uri]
  (let [shard-uris (get-shard-uris cluster-uri)]
    ;;(println "Make shard uris " shard-uris " read only")
    (doall (map make-shard-read-only shard-uris))))

(defn trigger-initial-sync
  "Test the impact of an initial sync on your cluster.
   Depending on the topology and parameters, it will either
   trigger a sync on a replica set member or on a sharded cluster,
   trigger a sync on one or more shards"
  [cluster-uri & all-shards]
  (if (is-sharded-cluster? cluster-uri)
    (force-initial-sync-sharded cluster-uri all-shards)
    (force-initial-sync-rs cluster-uri)))

(defn cause-random-chaos
  "Trigger random problem for how-long on any of the clusters listed in cluster-list, separated by a randomly varied interval of how-often"
  [cluster-list how-long max-wait & how-often]
  (let [rs-function-list (trigger-election make-rs-degraded make-rs-read-only)
        shard-function-list (make-shard-read-only make-config-servers-read-only make-random-shard-read-only make-sharded-cluster-read-only)]
    (while (not-expired? how-long)
      (if (is-sharded-cluster? (rand-nth cluster-list))
        (undo-operation (rand-nth shard-function-list) (rand max-wait))
        (undo-operation (rand-nth rs-function-list) (rand max-wait))))))

