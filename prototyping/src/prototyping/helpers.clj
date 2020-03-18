;; Helper functions for Mongodb discovery mechanisms

(in-ns 'prototyping.core)
(require '[clojure.string :as str]
         '[prototyping.conv-helpers :as pcv]
         '[prototyping.mini-driver :as md]
         '[clojure.java.shell :refer [sh]]
         '[clojurewerkz.urly.core :as urly]
         '[net.n01se.clojure-jna  :as jna]
         '[clj-ssh.ssh :as ssh])
(import  [java.lang ProcessBuilder]
         [com.mongodb ServerAddress MongoClientOptions MongoClientOptions$Builder ReadPreference]
         [com.mongodb.client MongoClient])

(defn- get-hostname
  []
  (.getCanonicalHostName (java.net.InetAddress/getLocalHost)))

(defn- admin-cmd
  "Internal - run admin command using Java driver 3.x API with proper read concern"
  [conn cmd]
  (pcv/from-bson-document (.runCommand (.getDatabase conn "admin") (pcv/to-bson-document cmd)) true))

(defn- admin-cmd-primary-pref
  "Internal - run admin command using Java driver 3.x API with proper read concern"
  [conn cmd]
  (pcv/from-bson-document (.runCommand (.getDatabase conn "admin") (pcv/to-bson-document cmd) (ReadPreference/primaryPreferred)) true))

;; Generally available helper functions

(defn make-mongo-uri
  [^String hostinfo]
  (if (str/starts-with? hostinfo "mongodb://")
    hostinfo
    (str "mongodb://" hostinfo)))

;; URI parsing helper
(defn- parse-mongodb-uri
  "Parses a mongodb URI and returns a map with :host, :port set
   TODO: Needs auth support"
  [uri]
  ;; Check if we're dealing with a list of servers first, because we'll have to split the URL in that case  
  (if (str/includes? uri ",")
    (let [parse-match (re-matches #"mongodb://.+/(.+)" uri)
          hosts       (str/split (nth parse-match 1) #",")]
      ;;(println "\nParse-match " parse-match)
      ;;(println "\nParsing host list " hosts "\n\n")
      (doall (map #(let [parsed-u (urly/url-like (make-mongo-uri % ))
                         host     (urly/host-of parsed-u)
                         port     (if (= (urly/port-of parsed-u) -1) 27017 (urly/port-of parsed-u))]
                     (ServerAddress. host port)) hosts)))
    (let [parsed-u (urly/url-like uri)
          host     (urly/host-of parsed-u)
          port     (if (= (urly/port-of parsed-u) -1) 27017 (urly/port-of parsed-u))]
      (if (= (urly/protocol-of parsed-u) "mongodb")
        { :host host :port port }
        { :host "" :port 9999 }))))

;;
;; A bunch of mongodb driver interface helpers
;;
;; (defn- run-cmd
;;   "Run a command, either on an existing connection or create a connection to run the command
;;    If the type of conn-info is a String, assume URI, otherwise assume MongoClient"
;;   ([conn-info cmd]
;;    (let [connection (if (= (type conn-info) String) (mg/connect (parse-mongodb-uri conn-info)) conn-info)
;;          cmd-result (mcmd/admin-command connection cmd)]
;;      (when (= (type conn-info) String)
;;        (mg/disconnect connection))
;;      cmd-result))
;;   ([conn-info cmd ^String username ^String password]
;;    (let [connection (if (= (type conn-info) String) (mg/connect (parse-mongodb-uri conn-info) username password) conn-info)
;;          cmd-result (mcmd/admin-command connection cmd)]
;;      (when (= (type conn-info) String)
;;        (mg/disconnect connection))
;;      cmd-result)))
(defn- run-serverstatus
  ([uri]
   (println "Trying to run server status on " uri "\n")
   (let [conn (md/mdb-connect uri)
         server-status (md/mdb-admin-command conn { :serverStatus 1 })]
     (md/mdb-disconnect conn)
     server-status))
  ([uri ^String username ^String password]
   (let [conn (md/mdb-connect uri username password)
         server-status (md/mdb-admin-command conn { :serverStatus 1 })]
     (md/mdb-disconnect conn)
     server-status)))

(defn- run-listshards
  "Returns the output of the mongodb listShards admin command"
  ([uri]
   (let [conn       (md/mdb-connect uri)
         shard-list (md/mdb-admin-command conn { :listShards 1 })]
     (md/mdb-disconnect conn)
     shard-list))
  ([uri ^String username ^String password]
   (let [conn       (md/mdb-connect uri username password)
         shard-list (md/mdb-admin-command conn { :listShards 1 })]
     (md/mdb-disconnect conn)
     shard-list)))

(defn- run-replset-get-config
  "Returns the output of mongodb's replSetGetConfig admin command"
  ([uri]   
   (let [conn          (md/mdb-connect uri)
        replset-config (md/mdb-admin-command conn { :replSetGetConfig 1 })]
    (md/mdb-disconnect conn)
    replset-config))
  ([uri ^String username ^String password]
   (let [conn           (md/mdb-connect uri username password)
         replset-config (md/mdb-admin-command conn { :replSetGetConfig 1 })]
     (md/mdb-disconnect conn)
     replset-config)))

(defn run-replset-get-status
  "Returns the result of Mongodb's replSetGetStatus admin command"
  ([uri]
   (let [conn           (md/mdb-connect uri)
         replset-status (md/mdb-admin-command conn {:replSetGetStatus 1} (ReadPreference/primaryPreferred))]
     (md/mdb-disconnect conn)
     replset-status))
  ([uri ^ReadPreference rp]
   (let [conn           (md/mdb-connect uri)
         replset-status (md/mdb-admin-command conn {:replSetGetStatus 1} rp)]
     (md/mdb-disconnect conn)
     replset-status))
  ([uri ^String username ^String password]
   (let [conn           (md/mdb-connect uri username password)
         replset-status (md/mdb-admin-command conn {:replSetGetStatus 1} (ReadPreference/primaryPreferred))]
     (md/mdb-disconnect conn)
     replset-status))
  ([uri ^String username ^String password ^ReadPreference rp]
   (let [conn           (md/mdb-connect uri username password)
         replset-status (md/mdb-admin-command conn {:replSetGetStatus 1} rp)]
     (md/mdb-disconnect conn)
     replset-status)))

(defn- run-get-shard-map
  "Returns the output of MongoDB's getShardMap admin command"
  [uri]
  (let [conn       (md/mdb-connect uri)
        shard-map  (md/mdb-admin-command conn { :getShardMap 1 })]
    (md/mdb-disconnect conn)
    shard-map))

(defn- run-replset-stepdown
  "Runs replSetStepdown to force an election"
  ([uri]
   (let [conn (md/mdb-connect uri)]
    (try
      (md/mdb-admin-command conn { :replSetStepDown 120 })
      (catch com.mongodb.MongoSocketReadException e
        ;;(println "Caught expected exception " e)))))
        ;;(println "Connection closed")
        (md/mdb-disconnect conn)))))
  ([uri ^String username ^String password]
   (let [conn (md/mdb-connect uri username password)]
     (try
       (md/mdb-admin-command conn { :replSetStepDown 120 })
       (catch com.mongodb.MongoSocketReadException e
        ;;(println "Caught expected exception " e)))))
        ;;(println "Connection closed")
         (md/mdb-disconnect conn))))))

(defn- run-shutdown-command
  "Run the shutdown command on a remote or local mongod/s"
  [conn-info & {:keys [force] :or {force false}}]
  ;;(println "\n\nTrying to shut down mongod at " conn-info " with force setting " force)
  ;; NOTE: running shutdown will trigger an exception as the database
  ;;       connection will close. Catch and discard the exception here
  (try
    (md/mdb-admin-command conn-info { :shutdown 1 :force force })
    (catch com.mongodb.MongoSocketReadException e
      (println "Exception caught as expected"))
    (catch java.lang.NullPointerException e
      (println "Caught NullPointerException"))
    (catch java.lang.RuntimeException e
      (println "Caught RuntimeException"))))

(defn- build-cmd-line-string
  [cmdline]
  ;;(println "cmdline type is " (type cmdline))
  (if (sequential? cmdline)
    (str/join " " cmdline)
    cmdline))

(defn- run-remote-ssh-command
  "Execute a command described by cmdline on the remote server 'server'"
  [server cmdline]
  (println "\nAttempting to run ssh command " cmdline "\n")
  (let [agent   (ssh/ssh-agent {})
        session (ssh/session agent server {:strict-host-key-checking :no})]
    (ssh/with-connection session
      (let [result (ssh/ssh session { :cmd (build-cmd-line-string cmdline) })]
        result))))

(defn- run-server-get-cmd-line-opts
  "Retrieve the server's command line options. Accepts either a uri or a MongoClient"
  ;;([conn-info]
  ;; (mcv/from-db-object (run-cmd conn-info { :getCmdLineOpts 1 }) true)))
  [^MongoClient conn]
  (println conn)
  (md/mdb-admin-command conn { :getCmdLineOpts 1 }))

(defn- run-server-status
  "Run the serverStatus command and return the result as a map"
  [^MongoClient conn]
  (md/mdb-admin-command conn { :serverStatus 1 }))

;; Replica set topology functions to
;; - Retrieve the connection URI for the primary/secondaries
;; - Get the number of nodes in an RS
(defn- get-rs-members-by-state
  ([uri state]
   (let [rs-state (run-replset-get-status uri)]
     ;;(println rs-state "\n")
     (filter #(= (get % :stateStr) state) (get rs-state :members))))
  ([uri state ^ReadPreference rp]
   (let [rs-state (run-replset-get-status uri rp)]
     ;;(println rs-state "\n")
     (filter #(= (get % :stateStr) state) (get rs-state :members))))
  ([uri state ^String user ^String pw]
   (let [rs-state (run-replset-get-status uri user pw)]
     ;;(println rs-state "\n")
     (filter #(= (get % :stateStr) state) (get rs-state :members))))
  ([uri state ^String user ^String pw ^ReadPreference rp]
   (let [rs-state (run-replset-get-status uri user pw rp)]
     ;;(println rs-state "\n")
     (filter #(= (get % :stateStr) state) (get rs-state :members)))))

(defn get-rs-primary
  "Retrieve the primary from a given replica set. Fails if URI doesn't point to a valid replica set"
  ([uri]
  ;;(println "\nTryin to get primary for replica set " uri "\n")
   (first (get-rs-members-by-state uri "PRIMARY")))
  ([uri ^ReadPreference rp]
   (first (get-rs-members-by-state uri "PRIMARY" rp)))
  ([uri ^String user ^String pw]
   ;;(println "\nTryin to get primary for replica set " uri "\n")
   (first (get-rs-members-by-state uri "PRIMARY" user pw)))
  ([uri ^String user ^String pw ^ReadPreference rp]
   (first (get-rs-members-by-state uri "PRIMARY" user pw rp))))


(defn get-rs-secondaries
  "Retrieve a list of secondaries for a given replica set. Fails if URI doesn't point to a valid replica set"
  ([uri]
   (get-rs-members-by-state uri "SECONDARY"))
  ([uri ^String user ^String pw]
   (get-rs-members-by-state uri "SECONDARY" user pw)))

(defn get-num-rs-members
  "Retrieve the number of members in a replica set referenced by its uri"
  ([uri]
   (count (get (run-replset-get-status uri) :members)))
  ([uri ^String username ^String password]
   (count (get (run-replset-get-status uri username password) :members))))

(defn num-active-rs-members
  "Return the number of 'active' replica set members that are either in PRIMARY or SECONDARY state"
  ([uri]
   (let [members (get (run-replset-get-status uri) :members)
         active-members (filter #(or (= (get % :stateStr) "PRIMARY") (= (get % :stateStr) "SECONDARY")) members)]
     (count active-members)))
  ([uri ^String user ^String pw]
   (let [members (get (run-replset-get-status uri user pw) :members)
         active-members (filter #(or (= (get % :stateStr) "PRIMARY") (= (get % :stateStr) "SECONDARY")) members)]
     (count active-members))))

(defn is-local-process?
  "Check if the mongo process referenced by the URI is local or not"
  [uri]
  (let [parsed-uri (urly/url-like (make-mongo-uri uri))
        hostname   (get-hostname)]
    (or (= (urly/host-of parsed-uri) "localhost") (= (urly/host-of parsed-uri) hostname))))

(defn- get-process-type
  ([uri]
   (let [proc-type (get (run-serverstatus uri) :process)]
     proc-type))
  ([uri ^String user ^String pw]
   (let [proc-type (get (run-serverstatus uri user pw) :process)]
     proc-type)))
  
(defn check-process-type
  ([parameters]
   (if (= (type parameters) String)
     (get-process-type parameters)
     (first parameters)))
  ([parameters ^String user ^String pw]
   (if (= (type parameters) String)
     (get-process-type parameters user pw)
     (first parameters))))

(defn is-mongod-process?
  "Check if the process referenced by the startup is a mongod process"
  ([parameters]
   (= (check-process-type parameters) "mongod"))
  ([parameters ^String user ^String pw]
   (= (check-process-type parameters user pw) "mongod")))

(defn is-mongos-process?
  "Check if the process referenced by the parameters seq is a mongos process"
  ([parameters]
   (= (check-process-type parameters) "mongos"))
  ([parameters ^String user ^String pw]
   (= (check-process-type parameters user pw) "mongos")))

(defn- spawn-process
  "Helper function that starts an external process"
  [process-parameters]
  (.waitFor (-> (ProcessBuilder. process-parameters) .inheritIO .start)))

(defn start-local-mongo-process [uri process-settings]
  ;;(println "\nStarting local mongo process on uri " uri " with parameters " process-settings)
  (spawn-process process-settings))


(defn- extract-server-name
  "Extract the server name portion from a mongodb uri"
  [uri]
  (let [parsed-uri (urly/url-like (make-mongo-uri uri))]
    (urly/host-of parsed-uri)))

(defn start-remote-mongo-process
  "Start a mongod/mongos on a different server.
   Connects via SSH to start to avoid the need
   for an agent on each server. The ssh account
   that the code is connecting with needs to have
   the appropriate privileges to start processes
   on the remote server."
   [uri cmdline]
  (run-remote-ssh-command (extract-server-name uri) cmdline))

(defn stop-mongo-process-impl
  "Behind the scenes implementation of mongo process shutdown.
   This is the shutdown via the MongoDB admin command. For
   externally triggered process shutdown, see the next function."
  ([uri]
   (let [conn (md/mdb-connect (make-mongo-uri uri))
         cmdline (run-server-get-cmd-line-opts conn)]
     (run-shutdown-command conn)
     (md/mdb-disconnect conn)
     cmdline))
  ([uri force]
   (let [conn (md/mdb-connect (make-mongo-uri uri))
         cmdline (run-server-get-cmd-line-opts conn)]
     (run-shutdown-command conn force)
     (md/mdb-disconnect conn)
     cmdline))
  ([uri ^String username ^String password]
   (let [conn    (md/mdb-connect uri username password)
         cmdline (run-server-get-cmd-line-opts conn)]
     (run-shutdown-command conn)
     (md/mdb-disconnect conn)
     cmdline))
  ([uri force ^String username ^String password]
   (let [conn (md/mdb-connect uri username password)
         cmdline (run-server-get-cmd-line-opts conn)]
     (run-shutdown-command conn force)
     (md/mdb-disconnect conn)
     cmdline)))

(defn- kill-local-mongo-process-impl
  "Kill the mongodb process via OS signal. There are two options:
     - force = false/nil - use SIGTERM for orderly shutdown
     - force = true      - use SIGKILL to simulate crash"
  ([uri force]
   (let [conn          (md/mdb-connect uri)
         cmd-line      (run-server-get-cmd-line-opts conn)
         server-status (run-server-status conn)
         pid           (get server-status :pid)]
     (md/mdb-disconnect conn)
     (if force
       (jna/invoke Integer c/kill pid 9)
       (jna/invoke Integer c/kill pid 15))
     cmd-line)))

(defn- kill-remote-mongo-process-impl
  "Kills a remote mongo process via OS signal. Similar functionality
   to the function above, but executed on a remote machine and thus
   using the 'kill' command rather than talking to the C library
   directly"
  ([uri force]
   (let [conn          (md/mdb-connect uri)
         cmd-line      (run-server-get-cmd-line-opts conn)
         server-status (run-server-status conn)
         pid           (get server-status :pid)]
     (md/mdb-disconnect conn)
     (run-remote-ssh-command (extract-server-name uri) (if force (str "kill -9 " pid) (str "kill " pid)))
     cmd-line))
  ([uri force ^String user ^String pw]
   (let [conn          (md/mdb-connect uri user pw)
         cmd-line      (run-server-get-cmd-line-opts conn)
         server-status (run-server-status conn)
         pid           (get server-status :pid)]
     (md/mdb-disconnect conn)
     (run-remote-ssh-command (extract-server-name uri) (if force (str "kill -9 " pid) (str "kill " pid)))
     cmd-line)))

(defn kill-mongo-process-impl
  ([uri]
   (if (is-local-process? uri)
     (kill-local-mongo-process-impl uri false)
     (kill-remote-mongo-process-impl uri false)))
  ([uri force]
   (if (is-local-process? uri)
     (kill-local-mongo-process-impl uri force)
     (kill-remote-mongo-process-impl uri force)))
  ([uri ^String user ^String pw]
   (if (is-local-process? uri)
     (kill-local-mongo-process-impl uri false user pw)
     (kill-remote-mongo-process-impl uri false user pw)))
  ([uri force ^String user ^String pw]
   (if (is-local-process? uri)
     (kill-local-mongo-process-impl uri force user pw)
     (kill-remote-mongo-process-impl uri force user pw))))

(defn send-mongo-rs-stepdown
  "Sends stepdown to the mongod referenced by the URI
   Note that the call requires a reconnect"
  ([uri]
   (run-replset-stepdown uri))
  ([uri ^String user ^String pw]
   (run-replset-stepdown uri user pw)))
  
(defn get-random-members
  "Returns a list of n random replica set members from the replica set referenced by uri"
  ([uri n]
  ;;(println "\nGetting random members for replset " uri "\n")
  (let [rs-members (get (run-replset-get-status uri) :members)]
    ;;(println "\nWorking on member list " rs-members "\n")
    (take n (shuffle rs-members))))
  ([uri n ^String username ^String password]
   ;;(println "\nGetting random members for replset " uri "\n")
   (let [rs-members (get (run-replset-get-status uri username password) :members)]
     ;;(println "\nWorking on member list " rs-members "\n")
     (take n (shuffle rs-members)))))

(defn get-random-shards
  "Returns a list of n random shards from the sharded cluster referenced by the uri"
  ([uri n]
   (let [shards (get (run-listshards uri) :shards)]
     (take n (shuffle shards))))
  ([uri n ^String username ^String password]
   (let [shards (get (run-listshards uri username password) :shards)]
     (take n (shuffle shards)))))

(defn get-config-servers-uri
  "Given a sharded cluster, returns the URI needed to connect to the config servers"
  ([cluster-uri]
   (let [shard-map (run-get-shard-map cluster-uri)]
     ;;(println shard-map)
     (str/split (get (get shard-map :map) :config) #",")))
  ([cluster-uri ^String username ^String password]
   (let [shard-map (run-get-shard-map cluster-uri username password)]
     ;;(println shard-map)
     (str/split (get (get shard-map :map) :config) #","))))

(defn- convert-shard-uri
  [from-list-shards]
  (let [components (re-matches #"(.*)/(.*)" from-list-shards)]
    (str "mongodb://" (get components 2) "/?replicaSet=" (get components 1))))

(defn get-shard-uris
  "Retrieve the URIs for the individual shards that make up the sharded cluster.
   cluster-uri _must_ point to the mongos for correct discovery.
   Note that listShards returns the shard replsets in a different format
   so we have to transform them before returning the list"
  ([cluster-uri]
  ;;(println "\nTrying to get shard uris for cluster " cluster-uri "\n")
   (let [shard-configs (run-listshards cluster-uri)]
     (map #(convert-shard-uri (get % :host)) (get shard-configs :shards))))
  ([cluster-uri ^String username ^String password]
   ;;(println "\nTrying to get shard uris for cluster " cluster-uri "\n")
   (let [shard-configs (run-listshards cluster-uri username password)]
     (map #(get % :host) (get shard-configs :shards)))))


(defn not-expired?
  "Check if the current time is still within the expected interval"
  [end-time]
  )

(defn is-sharded-cluster?
  "Check if the cluster specified by the URI is a sharded cluster or a replica set"
  ([uri]
   (try
     (let [shard-uris (get-shard-uris uri)] 
       (not (empty? shard-uris)))
     (catch com.mongodb.MongoCommandException e
       ;; Note - add log output just in case
       nil)))
  ([uri ^String user ^String pw]
   (try
     (let [shard-uris (get-shard-uris uri user pw)]
       (not (empty? shard-uris)))
     (catch com.mongodb.MongoCommandException e
       nil))))

(defn undo-operation
  "On functions that return a closure, execute the closure"
  [returned-closure]
  (returned-closure))

(defn get-server-cmdline
  "Retrieve the command line used to start this particular server process"
  ([server-uri]
   (get (run-server-get-cmd-line-opts server-uri) :argv))
  ([server-uri ^String user ^String password]
   (get (run-server-get-cmd-line-opts server-uri user password) :argv)))
