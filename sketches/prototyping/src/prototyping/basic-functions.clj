(in-ns 'prototyping.core)

(defn start-mongos-process
  [uri mongos-parameters]
  (if (is-local-process? uri)
    (start-local-mongo-process uri mongos-parameters)
    (start-remote-mongo-process uri mongos-parameters)))

(defn start-mongod-process
  "Start a mongo process (mongod or mongos) on the system listed in the URI with the parameters given. Fails if the process is already running or cannot be started"
  [uri mongod-parameters]
  (if (is-local-process? uri)
    (start-local-mongo-process uri mongod-parameters)
    (start-remote-mongo-process uri mongod-parameters)))

(defn start-mongo-process
  [uri mongo-parameters]
  ((println "\nAttempting to start mongod\n")
   (if (is-mongod-process? mongo-parameters)
     ((println "\nNeed to start mongod process\n") (start-mongod-process uri mongo-parameters))
     ((println "\nNeed to start mongos process\n") (start-mongos-process uri mongo-parameters)))))

(defn stop-mongo-process
  "Stop a local or remote mongo process (mongos or mongod) as listed by the URI. Fails if process isn't running or cannot be stopped"
  [uri]
  { :uri uri
    :cmd-line (get (stop-mongo-process-impl uri) :argv) } )

(defn restart-mongo-process
  "Stops and starts a mongo process"
  [uri &wait-time]
  (let [mongo-parameters (stop-mongo-process uri)]
    (start-mongo-process uri mongo-parameters)))

(defn stepdown-primary
  "Stepdown the primary for a replica set referenced by uri. Will error out if the URI doesn't point to a replica set or the RS has no primary"
  [uri]
  (let [primary (get (get-rs-primary uri) :name)]
    (println "\nStepping down primary " primary)
    (send-mongo-rs-stepdown (str "mongodb://" primary))))

(defn start-rs-nodes
  "Takes a list of URIs for mongod/mongos that need to be started"
  [uris]
  (map start-mongo-process uris))
