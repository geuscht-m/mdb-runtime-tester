;; Helper functions for Mongodb discovery mechanisms

(in-ns 'prototyping.core)
(require '[clojure.string :as str]
         '[monger.core :as mg]
         '[monger.command :as mcmd]
         '[monger.conversion :as mcv]
         '[clojure.java.shell :refer [sh]]
         '[clojurewerkz.urly.core :as urly])
(import  java.lang.ProcessBuilder)

;; URI parsing helper
(defn- parse-mongodb-uri
  "Parses a mongodb URI and returns a map with :host, :port set
   TODO: Needs auth support"
  [uri]
  (let [parsed-u (urly/url-like uri)]
    (if (= (urly/protocol-of parsed-u) "mongodb")
      { :host (urly/host-of parsed-u) :port (urly/port-of parsed-u) }
      { :host "" :port 9999 })))

;;
;; A bunch of mongodb driver interface helpers
;;
(defn- run-cmd
  "Run a command, either on an existing connection or create a connection to run the command
   If the type of conn-info is a String, assume URI, otherwise assume MongoClient"
  [conn-info cmd]
  (let [connection (if (= (type conn-info) String) (mg/connect (parse-mongodb-uri conn-info)) conn-info)
        cmd-result (mcmd/admin-command connection cmd)]
    (when (= (type conn-info) String)
      (mg/disconnect connection))
    cmd-result))

(defn- run-listshards
  "Returns the output of the mongodb listShards admin command"
  [uri]
  (let [conn (mg/connect (parse-mongodb-uri uri))
        shard-list (mcv/from-db-object (mcmd/admin-command conn { :listShards 1 }) true)]
    (mg/disconnect conn)
    shard-list))

(defn- run-replset-get-config
  "Returns the output of mongodb's replSetGetConfig admin command"
  [uri]
  (let [conn           (mg/connect (parse-mongodb-uri uri))
        replset-config (mcv/from-db-object (mcmd/admin-command conn { :replSetGetConfig 1 }) true)]
    (mg/disconnect conn)
    replset-config))

(defn- run-replset-get-status
  "Returns the result of Mongodb's replSetGetStatus admin command"
  [uri]
  (let [conn           (mg/connect (parse-mongodb-uri uri))
        replset-status (mcv/from-db-object (mcmd/admin-command conn { :replSetGetStatus 1 }) true)]
    (mg/disconnect conn)
    replset-status))

(defn- run-get-shard-map
  "Returns the output of MongoDB's getShardMap admin command"
  [uri]
  (let [conn       (mg/connect (parse-mongodb-uri uri))
        shard-map  (mcv/from-db-object (mcmd/admin-command conn { :getShardMap 1 }) true)]
    (mg/disconnect conn)
    shard-map))

(defn- run-replset-stepdown
  "Runs replSetStepdown to force an election"
  [uri]
  (let [conn (mg/connect (parse-mongodb-uri uri))]
    (try
      (mcv/from-db-object (mcmd/admin-command conn { :replSetStepDown 120 }) true)
      (catch com.mongodb.MongoSocketReadException e
        ;;(println "Caught expected exception " e)))))
        ;;(println "Connection closed")
        (mg/disconnect conn)))))
  

(defn- run-shutdown-command
  "Run the shutdown command on a remote or local mongod/s"
  [conn-info & {:keys [force] :or {force false}}]
  (println "\n\nTrying to shut down mongod at " conn-info " with force setting " force)
  ;; NOTE: running shutdown will trigger an exception as the database
  ;;       connection will close. Catch and discard the exception here
  (try
    (run-cmd conn-info { :shutdown 1 :force force })
    (catch com.mongodb.MongoSocketReadException e
      (println "Exception caught as expected"))
    (catch java.lang.NullPointerException e
      (println "Caught NullPointerException"))
    (catch java.lang.RuntimeException e
      (println "Caught RuntimeException"))))

(defn- run-remote-ssh-command
  [cmd]
  )

(defn- run-server-get-cmd-line-opts
  "Retrieve the server's command line options. Accepts either a uri or a MongoClient"
  [conn-info]
  (mcv/from-db-object (run-cmd conn-info { :getCmdLineOpts 1 }) true))

;; Replica set topology functions to
;; - Retrieve the connection URI for the primary/secondaries
;; - Get the number of nodes in an RS
(defn- get-rs-members-by-state
  [uri state]
  (let [rs-state (run-replset-get-status uri)]
    ;;(println rs-state "\n")
    (filter #(= (get % :stateStr) state) (get rs-state :members))))

(defn get-rs-primary
  "Retrieve the primary from a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri]
  (first (get-rs-members-by-state uri "PRIMARY")))

(defn get-rs-secondaries
  "Retrieve a list of secondaries for a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri]
  (get-rs-members-by-state uri "SECONDARY"))

(defn get-num-rs-members
  "Retrieve the number of members in a replica set referenced by its uri"
  [uri]
  (count (get (run-replset-get-status uri) :members)))

(defn num-active-rs-members
  "Return the number of 'active' replica set members that are either in PRIMARY or SECONDARY state"
  [uri]
  (let [members (get (run-replset-get-status uri) :members)
        active-members (filter #(or (= (get % :stateStr) "PRIMARY") (= (get % :stateStr) "SECONDARY")) members)]
    (count active-members)))

(defn is-local-process?
  "Check if the mongo process referenced by the URI is local or not"
  [uri]
  true)

(defn- get-process-type
  [uri]
  (let [conn (mg/connect (parse-mongodb-uri uri))
        proc-type (get (mcv/from-db-object (mcmd/server-status (mg/get-db conn "admin")) true) :process)]
    (mg/disconnect conn)
    proc-type))
  
(defn- make-mongo-uri
  [hostinfo]
  (str "mongodb://" hostinfo))

(defn check-process-type
  [parameters]
  (if (= (type parameters) String)
    (get-process-type parameters)
    (first parameters)))

(defn is-mongod-process?
  "Check if the process referenced by the startup is a mongod process"
  [parameters]
  (= (check-process-type parameters) "mongod"))

(defn is-mongos-process?
  "Check if the process referenced by the parameters seq is a mongos process"
  [parameters]
  (= (check-process-type parameters) "mongos"))

(defn- spawn-process
  "Helper function that starts an external process"
  [process-parameters]
  (.waitFor (-> (ProcessBuilder. process-parameters) .inheritIO .start)))

(defn start-local-mongo-process [uri process-settings]
  ;;(println "\n\nStarting mongod with parameters " process-settings "\n\n"))
  (println "\nStarting local mongo process on uri " uri " with parameters " process-settings)
  (spawn-process process-settings))

(defn start-remote-mongo-process [uri]
  (run-remote-ssh-command uri))

(defn stop-mongo-process-impl [uri]
  (let [conn (mg/connect (make-mongo-uri uri))
        cmdline (run-server-get-cmd-line-opts conn)]
    (run-shutdown-command conn)
    (mg/disconnect conn)
    cmdline))

;; (defn stop-mongos-process [uri]
;;   (let [conn (mg/connect uri)
;;         cmdline (run-server-get-cmd-line-opts conn)]
;;     (run-shutdown-command conn)
;;     cmdline))

(defn send-mongo-rs-stepdown
  "Sends stepdown to the mongod referenced by the URI
   Note that the call requires a reconnect"
  [uri]
  (run-replset-stepdown uri))
  
(defn get-random-members
  "Returns a list of n random replica set members from the replica set referenced by uri"
  [uri n]
  (let [rs-members (get (run-replset-get-status uri) :members)]
    (take n (shuffle rs-members))))

(defn get-random-shards
  "Returns a list of n random shards from the sharded cluster referenced by the uri"
  [uri n]
  (let [shards (get (run-listshards uri) :shards)]
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
    (map #(get % :host) (get shard-configs :shards))))

(defn not-expired?
  "Check if the current time is still within the expected interval"
  [end-time]
  )

(defn is-sharded-cluster?
  "Check if the cluster specified by the URI is a sharded cluster or a replica set"
  [uri]
  (not (empty? (get-shard-uris uri))))

(defn undo-operation
  "On functions that return a closure, execute the closure"
  [returned-closure]
  )

(defn get-server-cmdline
  "Retrieve the command line used to start this particular server process"
  [server-uri]
  (get (run-server-get-cmd-line-opts server-uri) :argv))
