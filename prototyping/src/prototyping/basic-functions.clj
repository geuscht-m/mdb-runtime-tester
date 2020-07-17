(in-ns 'prototyping.core)

(defn start-mongos-process
  [uri mongos-parameters]
  (if (is-local-process? uri)
    (start-local-mongo-process uri mongos-parameters)
    (start-remote-mongo-process uri mongos-parameters)))

(defn start-mongod-process
  "Start a mongo process (mongod or mongos) on the system listed in the URI with the parameters given. Fails if the process is already running or cannot be started"
  [uri mongod-parameters]
  ;;(println "Trying to start mongod process with parameters " mongod-parameters)
  (if (is-local-process? uri)
    (start-local-mongo-process uri mongod-parameters)
    (start-remote-mongo-process uri mongod-parameters)))

(defn start-mongo-process
  [uri mongo-parameters]
  ;;(println "\nAttempting to start mongod with parameters\n" uri mongo-parameters)
  ;;(println (type mongo-parameters))
  (if (is-mongod-process? mongo-parameters)
    (start-mongod-process uri mongo-parameters)
    (start-mongos-process uri mongo-parameters)))

(defn stop-mongo-process
  "Stop a local or remote mongo process (mongos or mongod) as listed by the URI. Fails if process isn't running or cannot be stopped"
  [uri & { :keys [force ^String user ^String pw ssl] :or { force false user nil pw nil ssl false } }]
  (println "Stopping process at " uri)
  { :uri uri :cmd-line (get (stop-mongo-process-impl uri :force force :username user :password pw :ssl ssl) :argv) })

(defn kill-mongo-process
  "Stop a local or remote mongo process (mongos or mongod) as listed by the URI. This function uses
   SIGTERM or SIGKILL to shut down the process rather than sending the process a shutdown command"
  ([uri]
   { :uri uri :cmd-line (get (kill-mongo-process-impl uri) :argv) })
  ([uri force]
   { :uri uri :cmd-line (get (kill-mongo-process-impl uri force) :argv) } )
  ([uri ^String user ^String pw]
   { :uri uri :cmd-line (get (kill-mongo-process-impl uri user pw) :argv) })
  ([uri force ^String user ^String pw]
   { :uri uri :cmd-line (get (kill-mongo-process-impl uri force user pw) :argv) } ))

(defn restart-mongo-process
  "Stops and starts a mongo process"
  [uri & { :keys [user password ssl] :or { user nil password nil ssl false}}]
  (println "Restarting mongo process on " uri " with username " user " and password " password)
  (let [mongo-parameters (stop-mongo-process uri :user user :pw password :ssl ssl)]
    ;;(println "Restarting mongo process at uri " uri " with parameters " mongo-parameters)
    (start-mongo-process (get mongo-parameters :uri) (get mongo-parameters :cmd-line))))

(defn stepdown-primary
  "Stepdown the primary for a replica set referenced by uri. Will error out if the URI doesn't point to a replica set or the RS has no primary"
  [uri & { :keys [user password ssl] :or { user nil password nil ssl false}}]
  (let [primary (get (get-rs-primary uri :user user :pw password :ssl ssl) :name)]
    (run-replset-stepdown (make-mongo-uri primary) :user user :password password :ssl ssl )))

(defn start-rs-nodes
  "Takes a list of URIs for mongod/mongos that need to be started"
  [uris]
  (map start-mongo-process uris))

(defn force-initial-sync-sharded
  [cluster-uri all-shards]
  )

(defn force-initial-sync-rs
  [rs-uri]
  )
