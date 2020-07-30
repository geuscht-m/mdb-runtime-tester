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
  [uri & { :keys [force ^String user ^String pwd ssl root-ca] :or { force false user nil pwd nil ssl false root-ca nil } }]
  ;;(println "Stopping process at " uri " with user " user " and password " pwd ", root-ca " root-ca)
  { :uri uri :cmd-line (get (stop-mongo-process-impl uri :force force :user user :pwd pwd :ssl ssl :root-ca root-ca) :argv) })

(defn kill-mongo-process
  "Stop a local or remote mongo process (mongos or mongod) as listed by the URI. This function uses
   SIGTERM or SIGKILL to shut down the process rather than sending the process a shutdown command"
  [uri & { :keys [ force user pwd ssl root-ca ] :or { force false user nil pwd nil ssl false root-ca nil } }]
  (let [result (kill-mongo-process-impl uri :force force :user user :pwd pwd :ssl ssl :root-ca root-ca)]
    ;;(println "kill-mongo-process-impl returned " result)
    { :uri uri :cmd-line (get result :argv) }))

(defn restart-mongo-process
  "Stops and starts a mongo process"
  [uri & { :keys [user pwd ssl] :or { user nil pwd nil ssl false}}]
  ;;(println "Restarting mongo process on " uri " with username " user " and password " pwd)
  (let [mongo-parameters (stop-mongo-process uri :user user :pwd pwd :ssl ssl)]
    ;;(println "Restarting mongo process at uri " uri " with parameters " mongo-parameters)
    (start-mongo-process (get mongo-parameters :uri) (get mongo-parameters :cmd-line))))

(defn stepdown-primary
  "Stepdown the primary for a replica set referenced by uri. Will error out if the URI doesn't point to a replica set or the RS has no primary"
  [uri & { :keys [user pwd ssl root-ca] :or { user nil pwd nil ssl false root-ca nil}}]
  (let [primary (get (get-rs-primary uri :user user :pwd pwd :ssl ssl :root-ca root-ca) :name)
        ssl-enabled (or ssl (.contains uri "ssl=true"))]
    (println "Trying to step down primary " primary " on replica set " uri ", root-ca " root-ca)
    (run-replset-stepdown (make-mongo-uri primary) :user user :pwd pwd :ssl ssl-enabled :root-ca root-ca)))

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
