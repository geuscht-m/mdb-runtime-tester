(ns prototyping.mini-driver)
;;(import [com.mongodb ServerAddress MongoClient MongoClientOptions MongoClientOptions$Builder ReadPreference])
(require          '[prototyping.conv-helpers :as pcv]
                  '[clojure.string           :as str])
(import [com.mongodb.client MongoClients MongoClient]
        [com.mongodb ConnectionString ReadPreference MongoCredential MongoClientSettings])

(defn ^MongoClient mdb-connect-with-uri
  "Use a MongoDB URI to connect to a mongos/mongod/replica set/cluster"
  [^String uri]
  (.create MongoClients uri))

(defn ^MongoClient mdb-connect
  ([^String mongo-uri]
   (if (str/starts-with? mongo-uri "mongodb://")
     (let [settings (ConnectionString. mongo-uri)]
       (MongoClients/create settings))
     (MongoClients/create mongo-uri)))
  ([^String mongo-uri ^String username ^String pwd]
   (println mongo-uri)
   (if (str/starts-with? mongo-uri "mongodb://")
     (let [settings (ConnectionString. mongo-uri)
           cred     (MongoCredential/createCredential username "admin" (char-array pwd))]
       (println "Attempting to connect to " mongo-uri)
       (println "With settiongs " settings)
       (MongoClients/create (.build (.credential (.applyConnectionString (MongoClientSettings/builder) settings) cred))))
     ((println "URI " mongo-uri " doesn't comply with URI format")
      nil))))

(defn mdb-disconnect
  [^MongoClient conn]
  (.close conn))

(defn mdb-run-command
  "Run a MongoDB command against a database"
  ([^MongoClient conn ^String dbname command]
   (pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command)) true))
  ([^MongoClient conn ^String dbname command ^ReadPreference pref]
   (println "Running command " command " with read preference " pref)
   (pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command) pref) true)))

(defn mdb-admin-command
  "Run a MongoDB command against the admin database"
  ([^MongoClient conn command]
   (mdb-run-command conn "admin" command))
  ([^MongoClient conn command ^ReadPreference pref]
   (println "\nRunning admin command " command " with read preference " pref)
   (mdb-run-command conn "admin" command pref)))
