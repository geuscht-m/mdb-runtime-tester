;; Helper functions for Mongodb discovery mechanisms

(in-ns 'prototyping.core)

;; Replica set topology functions to
;; - Retrieve the connection URI for the primary/secondaries
;; - Get the number of nodes in an RS
(defn get-rs-primary
  "Retrieve the primary from a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri]
  "")

(defn get-rs-secondaries
  "Retrieve a list of secondaries for a given replica set. Fails if URI doesn't point to a valid replica set"
  [uri]
  ())

(defn get-num-rs-members
  "Retrieve the number of members in a replica set referenced by its uri"
  [uri]
  0)


(defn is-local-process?
  "Check if the mongo process referenced by the URI is local or not"
  [uri]
  true)

(defn is-mongod-process?
  "Check if the process referenced by the URI is a mongod or mongos process"
  [uri]
  true)

(defn start-local-mongo-process [uri]
  ())

(defn start-remote-mongo-process [uri]
  ())

(defn stop-mongod-process [uri]
  ())

(defn stop-mongos-process [uri]
  ())


(defn send-mongo-rs-stepdown
  "Sends stepdown to the mongod referenced by the URI"
  [uri]
  )
  
(defn get-random-members
  "Returns a list of n random replica set members from the replica set referenced by uri"
  [uri n]
  )
