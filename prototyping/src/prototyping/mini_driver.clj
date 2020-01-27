(ns prototyping.mini-driver)
;;(import [com.mongodb ServerAddress MongoClient MongoClientOptions MongoClientOptions$Builder ReadPreference])
(require          '[prototyping.conv-helpers :as pcv]
                  '[clojure.string           :as str])
(import [com.mongodb.client MongoClients MongoClient]
        [com.mongodb ConnectionString])

(defn ^MongoClient mdb-connect-with-uri
  "Use a MongoDB URI to connecto to a mongos/mongod/replica set/cluster"
  [^String uri]
  (.create MongoClients uri))

(defn ^MongoClient mdb-connect
  [^String mongo-uri]
  (if (str/starts-with? mongo-uri "mongodb://")
    (let [settings (ConnectionString. mongo-uri)]
      (MongoClients/create settings))
    (MongoClients/create mongo-uri)))

(defn mdb-disconnect
  [^MongoClient conn]
  (.close conn))
  


;; (defn ^MongoClient mdb-connect
;;   "Connect to a mongos, mongod or replica set"
;;   ;;([^String uri]
;;   ;;(mdb-connect-with-uri uri))
;;   [conn-info]
;;   (case (type conn-info)
;;     ^String             (mdb-connect-with-uri conn-info)
;;     ^PersistentArrayMap (.create MongoClients conn-info)



(defn mdb-run-command
  "Run a MongoDB command against a database"
  [^MongoClient conn ^String dbname command]
  (pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command)) true))

(defn mdb-admin-command
  "Run a MongoDB command against the admin database"
  [^MongoClient conn command]
  (mdb-run-command conn "admin" command))
