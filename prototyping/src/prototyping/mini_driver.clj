(ns prototyping.mini-driver)
;;(import [com.mongodb ServerAddress MongoClient MongoClientOptions MongoClientOptions$Builder ReadPreference])
(require          '[prototyping.conv-helpers :as pcv]
                  '[clojure.string           :as str])
(import [com.mongodb.client MongoClients MongoClient MongoDatabase MongoCollection FindIterable]
        [com.mongodb ConnectionString ReadPreference MongoCredential MongoClientSettings])

(defn ^MongoClient mdb-connect-with-uri
  "Use a MongoDB URI to connect to a mongos/mongod/replica set/cluster"
  [^String uri]
  (.create MongoClients uri))

(defn ^MongoClient mdb-connect
  "Connect to a MongoDB database cluster using a URI and an optional
   authentication method"
  [mongo-uri & { :keys [ user pwd auth-method ] :or { user nil pwd nil auth-method nil ssl false }}]
  ;; Check if the user sent an authentication method or not.
  ;; If they didn't, default to SCRAM-SHA (username / password), otherwise connect using
  ;; the appropriate method
  (if (or (nil? auth-method) (str/starts-with? auth-method "SCRAM-SHA"))
    ;; Connect either without user information, or all of the authentication information
    ;; encoded in the URI connection string
    (cond (nil? user) (let [settings (ConnectionString. mongo-uri)]
                        (MongoClients/create settings))
          (and (not (nil? user))
               (not (nil? pwd))) (let [settings (ConnectionString. mongo-uri)
                                       cred     (MongoCredential/createCredential user "admin" (char-array pwd))]
                                   ;;(println "Attempting to connect to " mongo-uri)
                                   ;;(println "With settiongs " settings)
                                   (MongoClients/create
                                    (-> (MongoClientSettings/builder)
                                        (.applyConnectionString settings)
                                        (.credential cred)
                                        (.build)))))
    (cond (and (= auth-method "MONGODB-X509")
               (nil? user))        (MongoClients/create
                                    (-> (MongoClientSettings/builder)
                                        (.applyConnectionString (ConnectionString. mongo-uri))
                                        (.credential (MongoCredential/createMongoX509Credential))
                                        (.build)))
          (and (= auth-method "MONGODB-X509")
               (not (nil? user)))  (MongoClients/create
                                    (-> (MongoClientSettings/builder)
                                        (.applyConnectionString (ConnectionString. mongo-uri))
                                        (.credential (MongoCredential/createMongoX509Credential user))
                                        (.build)))
          false (println "Unsupported authentication method " auth-method))))

(defn mdb-disconnect
  "Close an existing connection to a MongoDB cluster"
  [^MongoClient conn]
  (.close conn))

(defn mdb-run-command
  "Run a MongoDB command against the database <db-name> and return the result
   as a map"
  [^MongoClient conn ^String dbname command & { :keys [ readPreference ] :or { readPreference nil } }]
  (if (nil? readPreference)
   (-> (.getDatabase conn dbname)
       (.runCommand (pcv/to-bson-document command))
       (pcv/from-bson-document ,, true))
   (-> (.getDatabase conn dbname)
       (.runCommand (pcv/to-bson-document command) readPreference)
       (pcv/from-bson-document ,, true))))

;; (defn mdb-run-command
;;   "Run a MongoDB command against a database"
;;   ([^MongoClient conn ^String dbname command]
;;    ;;(pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command)) true))
;;   ([^MongoClient conn ^String dbname command ^ReadPreference pref]
;;    ;;(println "Running command " command " with read preference " pref)
;;    (pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command) pref) true)))

(defn mdb-admin-command
  "Run a MongoDB command against the admin database"
  [^MongoClient conn command & { :keys [ readPreference ] :or { readPreference nil }}]
  (if (nil? readPreference)
    (mdb-run-command conn "admin" command)
    (mdb-run-command conn "admin" command :readPreference readPreference)))

(defn ^MongoDatabase mdb-get-database
  "Get a database with the name [db-name] from the MongoClient object"
  [^MongoClient conn db-name]
  (.getDatabase conn db-name))

(defn ^MongoCollection mdb-get-collection
  ""
  [^MongoDatabase db ^String coll-name]
  (.getCollection db coll-name))

(defn ^FindIterable mdb-find
  "Run a find command on MongoDB, returning a single document"
  [^MongoCollection coll criteria]
  (.find coll (pcv/to-bson-document criteria)))
