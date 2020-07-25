;; Helper functions for Mongodb discovery mechanisms

(in-ns 'prototyping.core)
(require '[clojure.string :as str]
         '[prototyping.conv-helpers :as pcv]
         '[prototyping.mini-driver :as md]
         '[prototyping.os-helpers :as os]
         '[clojure.java.shell :refer [sh]]
         '[clojurewerkz.urly.core :as urly]
         '[clj-ssh.ssh :as ssh])
(import  [com.mongodb ReadPreference]
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
  [uri & { :keys [ user pwd ssl root-ca ] :or { user nil pwd nil ssl false root-ca nil} } ]
  (let [conn          (md/mdb-connect uri :user user :pwd pwd :ssl ssl :root-ca root-ca)
        server-status (md/mdb-admin-command conn { :serverStatus 1 })]
    (md/mdb-disconnect conn)
    server-status))

(defn- run-listshards
  "Returns the output of the mongodb listShards admin command"
  [uri & { :keys [ username password ssl root-ca ] :or { username nil password nil ssl false root-ca nil} } ]
  (let [conn       (md/mdb-connect uri :user username :pwd password :ssl ssl)
        shard-list (md/mdb-admin-command conn { :listShards 1 })]
    (md/mdb-disconnect conn)
    shard-list))

(defn run-replset-get-status
  "Returns the result of Mongodb's replSetGetStatus admin command"
  [uri & { :keys [ read-preference user pwd ssl root-ca ] :or { read-preference nil user nil pwd nil ssl false root-ca nil} } ]
  ;;(println "Trying to run replset-get-status on " uri " with user " user " and password " pwd)
  (let [ssl-enabled    (or ssl (.contains uri "ssl=true"))
        conn           (md/mdb-connect uri :user user :pwd pwd :ssl ssl-enabled :root-ca root-ca)
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
  [uri & { :keys [ user pwd ssl root-ca ] :or { user nil pwd nil ssl false root-ca nil} }]
  (println "Attempting to step down primary at " uri " with user " user " and root-ca " root-ca)
  (let [conn (md/mdb-connect uri :user user :pwd pwd :ssl ssl :root-ca root-ca)]
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
        (get result :exit)))))

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
  [uri state & { :keys [ user pwd read-pref ssl root-ca ] :or { user nil pwd nil read-pref nil ssl false root-ca nil} } ]
  (let [rs-state (run-replset-get-status uri :user user :pwd pwd :read-preference read-pref :ssl ssl :root-ca root-ca)]
    ;;(println rs-state "\n")
    (filter #(= (get % :stateStr) state) (get rs-state :members))))

(defn get-rs-primary
  "Retrieve the primary from a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri & { :keys [ read-pref user pwd ssl root-ca] :or { read-pref (ReadPreference/primaryPreferred) user nil pwd nil ssl false root-ca nil}}]
  (println "Getting primary for rs " uri " with user " user ", root-ca " root-ca " and password " pwd)
  (first (get-rs-members-by-state uri "PRIMARY" :user user :pwd pwd :read-pref read-pref :ssl ssl :root-ca root-ca)))

(defn get-rs-secondaries
  "Retrieve a list of secondaries for a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri & { :keys [ read-pref user pwd ssl root-ca ] :or { read-pref (ReadPreference/primaryPreferred) user nil pwd nil ssl false root-ca nil}}]
  (println "Getting secondary for rs " uri ", user " user ", root-ca " root-ca)
  (get-rs-members-by-state uri "SECONDARY" :user user :pwd pwd :read-pref read-pref :ssl ssl :root-ca root-ca))

(defn get-num-rs-members
  "Retrieve the number of members in a replica set referenced by its uri"
  ([uri & { :keys [ user pwd ssl root-ca] :or { user nil pwd nil ssl false root-ca nil } }]
   (count (get (run-replset-get-status uri :user user :pwd pwd :ssl ssl :root-ca root-ca) :members))))

(defn num-active-rs-members
  "Return the number of 'active' replica set members that are either in PRIMARY or SECONDARY state"
  [uri & { :keys [ user pwd ssl root-ca ] :or { user nil pwd nil ssl false root-ca nil } }]
  (let [members        (get (run-replset-get-status uri :user user :pwd pwd :ssl ssl :read-preference (ReadPreference/primaryPreferred) :root-ca root-ca) :members)
        active-members (filter #(or (= (get % :stateStr) "PRIMARY") (= (get % :stateStr) "SECONDARY")) members)]
    ;;(println "num-active-members - count is " (count active-members))
    ;;(println "num-active-members - active-members is " active-members) 
    (count active-members)))

(defn is-local-process?
  "Check if the mongo process referenced by the URI is local or not"
  [uri]
  (let [parsed-uri (urly/url-like (make-mongo-uri uri))
        hostname   (os/get-hostname)]
    (or (= (urly/host-of parsed-uri) "localhost") (= (urly/host-of parsed-uri) hostname))))

(defn check-process-type
  "Retrieve the process type from serverstatus"
  [uri & { :keys [ user pwd ssl root-ca ] :or { user nil pwd nil ssl false root-ca nil} }]
  ;;(println "Trying to get process type for uri " uri " with user " user " and root-ca " root-ca)
  (if (= (type uri) String)
      (let [ssl-enabled (or ssl (.contains uri "ssl=true"))]
      ;;(get-process-type uri :user user :pwd pwd :ssl ssl :root-ca root-ca)
        (get (run-serverstatus uri :user user :pwd pwd :ssl ssl-enabled :root-ca root-ca) :process))
      (first uri)))

(defn is-mongod-process?
  "Check if the process referenced by the startup is a mongod process"
  [uri & { :keys [ user pwd ssl root-ca ] :or { user nil pwd nil ssl false root-ca nil } }]
  (= (check-process-type uri :user user :pwd pwd :ssl ssl :root-ca root-ca) "mongod"))

(defn is-mongos-process?
  "Check if the process referenced by the parameters seq is a mongos process"
  [uri & { :keys [ user pwd ssl root-ca ] :or { user nil pwd nil ssl false root-ca nil } }]
  (= (check-process-type uri :user user :pwd pwd :ssl ssl :root-ca root-ca) "mongos"))

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
  ([uri & { :keys [force ^String user ^String pwd ssl root-ca] :or { force false user nil pwd nil ssl false root-ca nil} } ]
   ;;(println "Stopping mongo process at uri " uri " with username " user " and password " pwd)
   (let [ssl-enabled (or ssl (.contains uri "ssl=true"))
         conn (md/mdb-connect uri :user user :pwd pwd :ssl ssl-enabled :root-ca root-ca)
         cmdline (run-server-get-cmd-line-opts conn)]
     (run-shutdown-command conn :force-shutdown force)
     (md/mdb-disconnect conn)
     cmdline)))

(defn kill-mongo-process-impl
  "Implementation of kill-mongo-process that distinguishes between a local and remote process, and
   calls the appropriate function to stop the mongo process."
  [uri & { :keys [force user pwd ssl root-ca] :or { force false user nil pwd nil ssl false root-ca nil }}]
  (let [conn          (md/mdb-connect uri :user user :pwd pwd :ssl ssl :root-ca root-ca)
        cmd-line      (run-server-get-cmd-line-opts conn)
        server-status (run-server-status conn)
        pid           (get server-status :pid)]
     (md/mdb-disconnect conn)     
     (if (is-local-process? uri)
       (os/kill-local-process pid force)
       (run-remote-ssh-command (extract-server-name uri) (if force (str "kill -9 " pid) (str "kill " pid))))
     cmd-line))
    
(defn get-random-members
  "Returns a list of n random replica set members from the replica set referenced by uri"
  [uri n & {:keys [^String user ^String pwd ssl root-ca] :or {user nil pwd nil ssl false root-ca nil}}]
  ;;(println "\nGetting random members for replset " uri "\n")
  (let [rs-members (get (run-replset-get-status uri :user user :pwd pwd :ssl ssl :root-ca root-ca) :members)]
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
