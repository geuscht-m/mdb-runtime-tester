(ns prototyping.mini-driver)
;;(import [com.mongodb ServerAddress MongoClient MongoClientOptions MongoClientOptions$Builder ReadPreference])
(require          '[prototyping.conv-helpers :as pcv]
                  '[clojure.string           :as str])
(import [com.mongodb.client MongoClients MongoClient]
        [com.mongodb ConnectionString ReadPreference MongoCredential MongoClientSettings$Builder])

(defn ^MongoClient mdb-connect-with-uri
  "Use a MongoDB URI to connecto to a mongos/mongod/replica set/cluster"
  [^String uri]
  (.create MongoClients uri))

(defn ^MongoClient mdb-connect
  ([^String mongo-uri]
   (if (str/starts-with? mongo-uri "mongodb://")
     (let [settings (ConnectionString. mongo-uri)]
       (MongoClients/create settings))
     (MongoClients/create mongo-uri)))
  ([^String mongo-uri ^String username ^String pwd]
   (if (str/starts-with? mongo-uri "mongodb://")
     (let [settings (ConnectionString. mongo-uri)
           cred     (MongoCredential/createCredential username "admin" (char-array pwd))]
       (MongoClients/create (.build (.credential (.applyConnectionString (.builder MongoClientSettings$Builder) settings) cred))))
     nil)))

(defn mdb-disconnect
  [^MongoClient conn]
  (.close conn))

(defn mdb-run-command
  "Run a MongoDB command against a database"
  ([^MongoClient conn ^String dbname command]
   (pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command)) true))
  ([^MongoClient conn ^String dbname command ^ReadPreference pref]
   (pcv/from-bson-document (.runCommand (.withReadPreference (.getDatabase conn dbname) pref) (pcv/to-bson-document command)) true)))

(defn mdb-admin-command
  "Run a MongoDB command against the admin database"
  ([^MongoClient conn command]
   (mdb-run-command conn "admin" command))
  ([^MongoClient conn command ^ReadPreference pref]
   (mdb-run-command conn "admin" command pref)))
