(in-ns 'prototyping.core)

(defn start-mongos-process
  [uri mongos-parameters]
  (if (is-local-process? uri)
    (start-local-mongo-process uri mongos-parameters)
    (start-remote-mongo-process uri mongos-parameters)))

(defn start-mongod-process
  "Start a mongo process (mongod or mongos) on the system listed in the URI with the parameters given. Fails if the process is already running or cannot be started"
  [uri mongod-parameters]
  (println "Trying to start mongod process with paramters " mongod-parameters)
  (if (is-local-process? uri)
    (start-local-mongo-process uri mongod-parameters)
    (start-remote-mongo-process uri mongod-parameters)))

(defn start-mongo-process
  [uri mongo-parameters]
  (println "\nAttempting to start mongod with parameteærs\n" uri mongo-parameters)
  (println (type mongo-parameters))
  (if (is-mongod-process? mongo-parameters)
    (start-mongod-process uri mongo-parameters)
    (start-mongos-process uri mongo-parameters)))

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
    (send-mongo-rs-stepdown (make-mongo-uri primary))))

(defn start-rs-nodes
  "Takes a list of URIs for mongod/mongos that need to be started"
  [uris]
  (map start-mongo-process uris))
