(in-ns 'prototyping.core)

(defn start-mongo-process
  "Start a mongo process (mongod or mongos) on the system listed in the URI. Fails if the process is already running or cannot be started"
  [uri]
  (if (is-local-process? uri)
    (start-local-mongo-process uri)
    (start-remote-mongo-process uri)))

(defn stop-mongo-process
  "Stop a local or remote mongo process (mongos or mongod) as listed by the URI. Fails if process isn't running or cannot be stopped"
  [uri]
  (if (is-mongod-process? uri)
    (stop-mongod-process uri)
    (stop-mongos-process uri)))

(defn restart-mongo-process
  "Stops and starts a mongo process"
  [uri &wait-time]
  ((stop-mongo-process uri)
   (start-mongo-process uri)))

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
