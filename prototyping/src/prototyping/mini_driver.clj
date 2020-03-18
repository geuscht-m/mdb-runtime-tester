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
  "Connect to a MongoDB database cluster using a URI and an optional
   authentication method"
  [mongo-uri & { :keys [ user pwd auth-method ] :or { user nil pwd nil auth-method nil }}]
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
                                   (MongoClients/create (.build (.credential (.applyConnectionString (MongoClientSettings/builder) settings) cred)))))
    (cond (= auth-method "MONGODB-X509") nil
          false (println "Unsupported authentication method " auth-method))))

(defn mdb-disconnect
  [^MongoClient conn]
  (.close conn))

(defn mdb-run-command
  "Run a MongoDB command against a database"
  ([^MongoClient conn ^String dbname command]
   (pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command)) true))
  ([^MongoClient conn ^String dbname command ^ReadPreference pref]
   ;;(println "Running command " command " with read preference " pref)
   (pcv/from-bson-document (.runCommand (.getDatabase conn dbname) (pcv/to-bson-document command) pref) true)))

(defn mdb-admin-command
  "Run a MongoDB command against the admin database"
  ([^MongoClient conn command]
   (mdb-run-command conn "admin" command))
  ([^MongoClient conn command ^ReadPreference pref]
   ;;(println "\nRunning admin command " command " with read preference " pref)
   (mdb-run-command conn "admin" command pref)))
