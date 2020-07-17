;; Helper functions for Mongodb discovery mechanisms

(in-ns 'prototyping.core)
(require '[clojure.string :as str]
         '[prototyping.conv-helpers :as pcv]
         '[prototyping.mini-driver :as md]
         '[prototyping.os-helpers :as os]
         '[clojure.java.shell :refer [sh]]
         '[clojurewerkz.urly.core :as urly]
         '[clj-ssh.ssh :as ssh])
(import  [com.mongodb ServerAddress MongoClientOptions MongoClientOptions$Builder ReadPreference]
         [com.mongodb.client MongoClient])


;; Generally available helper functions

(defn make-mongo-uri
  [^String hostinfo]
  ;;(println "make-mongo-uri " hostinfo)
  (if (str/starts-with? hostinfo "mongodb://")
    hostinfo
    (str "mongodb://" hostinfo)))

;; Local helper functions, not exposed to other namespaces

(defn- run-serverstatus
  [uri & { :keys [ user password ssl ] :or { user nil password nil ssl false } } ]
  (let [conn          (md/mdb-connect uri :user user :pwd password :ssl ssl)
        server-status (md/mdb-admin-command conn { :serverStatus 1 })]
    (md/mdb-disconnect conn)
    server-status))

(defn- run-listshards
  "Returns the output of the mongodb listShards admin command"
  [uri & { :keys [ username password ssl ] :or { username nil password nil ssl false } } ]
  (let [conn       (md/mdb-connect uri :user username :pwd password :ssl ssl)
        shard-list (md/mdb-admin-command conn { :listShards 1 })]
    (md/mdb-disconnect conn)
    shard-list))

(defn run-replset-get-status
  "Returns the result of Mongodb's replSetGetStatus admin command"
  [uri & { :keys [ read-preference user password ssl ] :or { read-preference nil user nil password nil ssl false } } ]
  (println "Trying to run replset-get-status on " uri " with user " user " and password " password)
  (let [conn           (md/mdb-connect uri :user user :pwd password :ssl ssl)
        replset-status (md/mdb-admin-command conn {:replSetGetStatus 1} :readPreference read-preference)]
    (md/mdb-disconnect conn)
    replset-status))

(defn- run-get-shard-map
  "Returns the output of MongoDB's getShardMap admin command"
  [uri]
  (let [conn       (md/mdb-connect uri)
        shard-map  (md/mdb-admin-command conn { :getShardMap 1 })]
    (md/mdb-disconnect conn)
    shard-map))

(defn- run-replset-stepdown
  "Runs replSetStepdown to force an election"
  [uri & { :keys [ user password ssl ] :or { user nil password nil ssl false } }]
  (let [conn (md/mdb-connect uri :user user :pwd password :ssl ssl)]
    (try
      (md/mdb-admin-command conn { :replSetStepDown 120 })
      (catch com.mongodb.MongoSocketReadException e
        ;;(println "Caught expected exception " e)))))
        ;;(println "Connection closed")
        (md/mdb-disconnect conn)))))
   

(defn- run-shutdown-command
  "Run the shutdown command on a remote or local mongod/s"
  [conn-info & { :keys [ force-shutdown ] :or { force-shutdown false } } ]
  ;;(println "\n\nTrying to shut down mongod at " conn-info " with force setting " force)
  ;; NOTE: running shutdown will trigger an exception as the database
  ;;       connection will close. Catch and discard the exception here
  (try
    (md/mdb-admin-command conn-info { :shutdown 1 :force force-shutdown })
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
  ;;(println "\nAttempting to run ssh command " cmdline "\n")
  (let [agent   (ssh/ssh-agent {})
        session (ssh/session agent server {:strict-host-key-checking :no})]
    (ssh/with-connection session
      (let [result (ssh/ssh session { :cmd (build-cmd-line-string cmdline) })]
        result))))

(defn- run-server-get-cmd-line-opts
  "Retrieve the server's command line options. Accepts either a uri or a MongoClient"
  [^MongoClient conn]
  (md/mdb-admin-command conn { :getCmdLineOpts 1 }))

(defn- run-server-status
  "Run the serverStatus command and return the result as a map"
  [^MongoClient conn]
  (md/mdb-admin-command conn { :serverStatus 1 }))

;; Replica set topology functions to
;; - Retrieve the connection URI for the primary/secondaries
;; - Get the number of nodes in an RS
(defn- get-rs-members-by-state
  [uri state & { :keys [ user pw read-pref ssl ] :or { user nil pw nil read-pref nil ssl false } } ]
  (let [rs-state (run-replset-get-status uri :user user :password pw :read-preference read-pref :ssl ssl)]
    ;;(println rs-state "\n")
    (filter #(= (get % :stateStr) state) (get rs-state :members))))

(defn get-rs-primary
  "Retrieve the primary from a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri & { :keys [ read-pref user pw ssl ] :or { read-pref (ReadPreference/primaryPreferred) user nil pw nil ssl false }}]
  (first (get-rs-members-by-state uri "PRIMARY" :user user :pw pw :read-pref read-pref :ssl ssl)))


(defn get-rs-secondaries
  "Retrieve a list of secondaries for a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri & { :keys [ read-pref user pw ssl ] :or { read-pref (ReadPreference/primaryPreferred) user nil pw nil ssl false }}]
  (get-rs-members-by-state uri "SECONDARY" :user user :pw pw :read-pref read-pref :ssl ssl))

(defn get-num-rs-members
  "Retrieve the number of members in a replica set referenced by its uri"
  ([uri & { :keys [ user pwd ssl ] :or { user nil pwd nil ssl false } }]
   (count (get (run-replset-get-status uri :user user :password pwd :ssl ssl) :members))))

(defn num-active-rs-members
  "Return the number of 'active' replica set members that are either in PRIMARY or SECONDARY state"
  [uri & { :keys [ user pw ssl ] :or { user nil pw nil ssl false } }]
  (let [members (get (run-replset-get-status uri :user user :password pw :ssl ssl :read-preference (ReadPreference/primaryPreferred)) :members)
        active-members (filter #(or (= (get % :stateStr) "PRIMARY") (= (get % :stateStr) "SECONDARY")) members)]
    (count active-members)))

(defn is-local-process?
  "Check if the mongo process referenced by the URI is local or not"
  [uri]
  (let [parsed-uri (urly/url-like (make-mongo-uri uri))
        hostname   (os/get-hostname)]
    (or (= (urly/host-of parsed-uri) "localhost") (= (urly/host-of parsed-uri) hostname))))

(defn- get-process-type
  [uri & { :keys [ user pw ssl ] :or { user nil pw nil ssl false } }]
  (let [proc-type (get (run-serverstatus uri :user user :password pw :ssl ssl) :process)]
     proc-type))
  
(defn check-process-type
  [uri & { :keys [ user pw ssl ] :or { user nil pw nil ssl false } }]
  (if (= (type uri) String)
    (get-process-type uri :user user :pw pw :ssl ssl)
    (first uri)))

(defn is-mongod-process?
  "Check if the process referenced by the startup is a mongod process"
  ([parameters]
   (= (check-process-type parameters) "mongod"))
  ([parameters ^String user ^String pw]
   (= (check-process-type parameters :user user :pw pw) "mongod")))

(defn is-mongos-process?
  "Check if the process referenced by the parameters seq is a mongos process"
  ([parameters]
   (= (check-process-type parameters) "mongos"))
  ([parameters ^String user ^String pw]
   (= (check-process-type parameters :user user :pw pw) "mongos")))

(defn start-local-mongo-process [uri process-settings]
  ;;(println "\nStarting local mongo process on uri " uri " with parameters " process-settings)
  (os/spawn-process process-settings))


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
  ([uri & { :keys [force ^String username ^String password ssl] :or { force false username nil password nil ssl false } } ]
   (println "Stopping mongo process at uri " uri " with username " username " and password " password)
   (let [conn (md/mdb-connect uri :user username :pwd password :ssl ssl)
         cmdline (run-server-get-cmd-line-opts conn)]
     (run-shutdown-command conn :force-shutdown force)
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
     (os/kill-local-process pid force)
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
   (let [conn          (md/mdb-connect uri :user user :pwd pw)
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
   (run-replset-stepdown uri :user user :password pw)))
  
(defn get-random-members
  "Returns a list of n random replica set members from the replica set referenced by uri"
  [uri n & {:keys [^String user ^String pwd ssl] :or {user nil pwd nil ssl false}}]
  ;;(println "\nGetting random members for replset " uri "\n")
  (let [rs-members (get (run-replset-get-status uri :user user :password pwd :ssl ssl) :members)]
    ;;(println "\nWorking on member list " rs-members "\n")
    (take n (shuffle rs-members))))


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
