(in-ns 'tester-core.core)

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
  (timbre/debug "Attempting to start mongo process at " uri " with parameters "  mongo-parameters)
  (if (is-mongod-process? mongo-parameters)
    (start-mongod-process uri mongo-parameters)
    (start-mongos-process uri mongo-parameters)))

(defn stop-mongo-process
  "Stop a local or remote mongo process (mongos or mongod) as listed by the URI. Fails if process isn't running or cannot be stopped"
  [uri & { :keys [force ^String user ^String pwd ssl root-ca client-cert auth-mechanism] :as opts }]
  (timbre/debug "Stopping process at " uri " with user " user ", root-ca " root-ca " and auth-mechanism" auth-mechanism)
  { :uri uri :cmd-line (get (apply stop-mongo-process-impl uri (mapcat identity opts)) :argv) })

(defn kill-mongo-process
  "Stop a local or remote mongo process (mongos or mongod) as listed by the URI. This function uses
   SIGTERM or SIGKILL to shut down the process rather than sending the process a shutdown command"
  [uri & { :keys [ force user pwd ssl root-ca client-cert auth-mechanism ] :as opts }]
  (let [result (apply kill-mongo-process-impl uri (mapcat identity opts))]
    ;;(println "kill-mongo-process-impl returned " result)
    { :uri uri :cmd-line (get result :argv) }))

(defn restart-mongo-process
  "Stops and starts a mongo process"
  [uri & { :keys [user pwd ssl root-ca client-cert auth-mechanism] :as opts }]
  ;;(println "Restarting mongo process on " uri " with username " user " and password " pwd)
  (let [mongo-parameters (apply stop-mongo-process uri (mapcat identity opts))]
    ;;(println "Restarting mongo process at uri " uri " with parameters " mongo-parameters)
    (start-mongo-process (:uri mongo-parameters) (:cmd-line mongo-parameters))))

(defn stepdown-primary
  "Stepdown the primary for a replica set referenced by uri. Will error out if the URI doesn't point to a replica set or the RS has no primary"
  [rs-uri & { :keys [user pwd ssl root-ca client-cert auth-mechanism] :as opts}]
  (let [primary (:name (apply get-rs-primary rs-uri (mapcat identity opts)))
        has-ssl (or (.contains rs-uri "ssl=true") (.contains rs-uri "tls=true") :ssl)
        primary-uri (str/join "" [(make-mongo-uri primary) (if has-ssl "/&tls=true" "/&tls=false")])]
    (timbre/debug "Trying to step down primary " primary " with uri " primary-uri " on replica set " rs-uri ", root-ca " root-ca)
    (apply run-replset-stepdown primary-uri (mapcat identity opts))))

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
