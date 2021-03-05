(ns tester-core.mini-driver
  (:require [tester-core.conv-helpers :as pcv]
            [clojure.string           :as str]
            [clj-pem-decoder.core     :as pem :refer :all]
            [taoensso.timbre :as timbre :refer [debug]]))
(import [com.mongodb.client MongoClients MongoClient MongoDatabase MongoCollection FindIterable]
        [com.mongodb ConnectionString ReadPreference MongoCredential MongoClientSettings Block MongoCommandException]
        [com.mongodb.connection SslSettings]
        [javax.net.ssl SSLContext TrustManagerFactory KeyManagerFactory]
        [java.security KeyStore SecureRandom]
        [java.security.cert CertificateFactory]
        [java.io FileInputStream])

(defn- loadX509Cert
  [cert-path]
  (with-open [in-file (FileInputStream. cert-path)]
    (let [cf       (CertificateFactory/getInstance "X.509")
          cert     (.generateCertificate cf in-file)
          subject  (.getName (.getSubjectDN cert))]
      ;;(println "Certificate is " cert)
      ;;(println "Subject is " subject)
      {:subject subject :cert cert})))

(defn- ^SSLContext ssl-context-with-ca
  "Create an SSLContext for use by the driver using a supplied
   root CA file. Needed for untrusted and self-signed certificates"
  [root-ca-file]
  ;;(println "Building SSLContext from root certificate file " root-ca-file)
  (let [ca-cert (first (get (pem/decode-pem root-ca-file) :certificates))]
    ;;(println "ca-cert is " ca-cert)
    (let [ks      (doto (KeyStore/getInstance (KeyStore/getDefaultType))
                    (.load nil)
                    (.setCertificateEntry "caCert" ca-cert))]
      ;;(println "KeyStore ks is " ks)
      (let [tmf     (doto (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
                      (.init ks))]
        ;;(println "TrustManagerFactory tmf is" tmf)
        (doto (SSLContext/getInstance "TLS")
          (.init nil (.getTrustManagers tmf) nil))))))

(defn- ^SSLContext ssl-context-with-client-cert
  "Create an SSL Context with an optional root CA
   and a client certificate for x.509"
  [root-ca-file client-cert]
  ;;(println "Creating SSLContext with root-ca " root-ca-file " and client-cert " client-cert)
  (let [user-cert (pem/decode-pem client-cert)]
    ;;(println "user cert is " user-cert ", read from " client-cert)
    (let [x509-key  (get user-cert :private-key)
          x509-cert (get user-cert :certificates)
          ca-cert   (pem/decode-pem root-ca-file)
        ks-root   (doto (KeyStore/getInstance (KeyStore/getDefaultType))
                    (.load nil)
                    (.setCertificateEntry "caCert" (first (get ca-cert :certificates))))
        ks-client (doto (KeyStore/getInstance (KeyStore/getDefaultType))
                    (.load nil)
                    (.setCertificateEntry (.getName (.getSubjectDN (first x509-cert))) (first x509-cert))
                    (.setKeyEntry         "private-key" x509-key (.toCharArray "test") x509-cert))]
    ;;(println "KeyManagerFactory default algorithm is " (KeyManagerFactory/getDefaultAlgorithm))
    (let [tmf     (doto (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
                    (.init ks-root))
          kmf     (doto (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
                    (.init ks-client (char-array "test")))
          context (doto (SSLContext/getInstance "TLS")
                    (.init (.getKeyManagers kmf) (.getTrustManagers tmf) (SecureRandom.)))]
      ;;(println "kmf is " kmf " and ks-client is " ks-client ", getKeyManagers returns " (.getKeyManagers kmf) " with " (count (.getKeyManagers kmf)) "key managers")
      { :subject (get x509-cert :subject) :ssl-context context}))))

(defn- create-ssl-mongo-client-no-ssl-context
  [mongo-uri ssl]
  ;;(println "Creating non-user auth without ssl context")
  (MongoClients/create
   (-> (MongoClientSettings/builder)
       (.applyConnectionString (ConnectionString. mongo-uri))
       (.applyToSslSettings(reify
                             com.mongodb.Block
                             (apply [this s] (-> s
                                                 (.enabled ssl)
                                                 (.build)))))
       (.build))))

(defn- create-ssl-mongo-client-with-ssl-context
  [mongo-uri ssl root-ca]
  ;;(println "Creating non-user auth connection with ssl context, ssl enabled " ssl)
  (MongoClients/create
   (-> (MongoClientSettings/builder)
       (.applyConnectionString (ConnectionString. mongo-uri))
       (.applyToSslSettings(reify
                             com.mongodb.Block
                             (apply [this s] (-> s
                                                 (.enabled ssl)
                                                 (.context (ssl-context-with-ca root-ca))
                                                 (.build)))))
       (.build))))

(defn- create-ssl-mongo-client-scram-no-ssl-context
  [mongo-uri ssl user pwd]
  (let [settings (ConnectionString. mongo-uri)
        cred     (MongoCredential/createCredential user "admin" (char-array pwd))]
    ;;(println "Creating SCRAM connection to " mongo-uri " for user " user " with default SSL context")
    (MongoClients/create
     (-> (MongoClientSettings/builder)
         (.applyConnectionString (ConnectionString. mongo-uri))                                          
         (.applyToSslSettings(reify
                               com.mongodb.Block
                               (apply [this s] (-> s
                                                   (.enabled ssl)
                                                   (.build)))))
         (.credential cred)
         (.build)))))

(defn- create-ssl-mongo-client-scram-with-ssl-context
  [mongo-uri ssl user pwd root-ca]
  (let [settings (ConnectionString. mongo-uri)
        cred     (MongoCredential/createCredential user "admin" (char-array pwd))]
    ;;(println "Creating SSL SCRAM connection to " mongo-uri " for user " user " with root certificate " root-ca)
    (MongoClients/create
     (-> (MongoClientSettings/builder)
         (.applyConnectionString (ConnectionString. mongo-uri))
         (.applyToSslSettings(reify
                               com.mongodb.Block
                               (apply [this s] (-> s
                                                   (.enabled ssl)
                                                   (.context (ssl-context-with-ca root-ca))
                                                   (.build)))))
         (.credential cred)
         (.build)))))
  

(defn- create-client-with-ssl
  [mongo-uri ssl root-ca]
  (if (nil? root-ca)
    (create-ssl-mongo-client-no-ssl-context mongo-uri ssl)
    (create-ssl-mongo-client-with-ssl-context mongo-uri ssl root-ca)))

(defn- create-x509-client-with-ssl-and-client-cert
  [mongo-uri root-ca client-cert]
  (let [client-context (ssl-context-with-client-cert root-ca client-cert)
        cred           (MongoCredential/createMongoX509Credential (get client-context :subject))]
    ;;(println "client-context is " client-context)
    ;;(println "cred is " cred)
    (MongoClients/create
     (-> (MongoClientSettings/builder)
         (.applyConnectionString (ConnectionString. mongo-uri))
         (.applyToSslSettings(reify
                               com.mongodb.Block
                               (apply [this s] (-> s
                                                   (.enabled true)
                                                   (.context (get client-context :ssl-context))
                                                   (.build)))))
         (.credential cred)
         (.build)))))

(defn- create-scram-client-with-ssl
  [mongo-uri user pwd ssl root-ca]
  (if (nil? root-ca)
    (create-ssl-mongo-client-scram-no-ssl-context mongo-uri ssl user pwd)
    (create-ssl-mongo-client-scram-with-ssl-context mongo-uri ssl user pwd root-ca)))
  

(defn ^MongoClient mdb-connect-with-uri
  "Use a MongoDB URI to connect to a mongos/mongod/replica set/cluster"
  [^String uri]
  (.create MongoClients uri))

(defn ^MongoClient mdb-connect
  "Connect to a MongoDB database cluster using a URI and an optional
   authentication method"
  [mongo-uri & { :keys [ user pwd auth-mechanism ssl root-ca client-cert ] :or { user nil pwd nil auth-mechanism nil ssl false root-ca nil client-cert nil}}]
  ;; Check if the user sent an authentication method or not.
  ;; If they didn't, default to SCRAM-SHA (username / password), otherwise connect using
  ;; the appropriate method
  (println "Trying to connect to " mongo-uri " with user " user ", password", pwd ", ssl " ssl ", root-ca " root-ca ", client-cert " client-cert ", auth-mechanism " auth-mechanism)
  (if (or (nil? auth-mechanism) (str/starts-with? auth-mechanism "SCRAM-SHA"))
    ;; Connect either without user information, or all of the authentication information
    ;; encoded in the URI connection string
    (do
      (println "Trying to connect with SCRAM-SHA")
      (let [ssl-enabled (or ssl (.contains mongo-uri "ssl=true"))]
        (cond (nil? user) (if ssl-enabled
                            (create-client-with-ssl mongo-uri ssl-enabled root-ca)
                            (MongoClients/create (ConnectionString. mongo-uri)))
              (and (not (nil? user))
                   (not (nil? pwd))) (create-scram-client-with-ssl mongo-uri user pwd ssl-enabled root-ca))))
    (cond (and (= auth-mechanism :mongodb-x509) (nil? user))
          (create-x509-client-with-ssl-and-client-cert mongo-uri root-ca client-cert)
          (and (= auth-mechanism :mongodb-x509)
               (not (nil? user)))  (MongoClients/create
                                    (-> (MongoClientSettings/builder)
                                        (.applyConnectionString (ConnectionString. mongo-uri))
                                        (.credential (MongoCredential/createMongoX509Credential user))
                                        (.build)))
          false (timbre/error "Unsupported authentication method " auth-mechanism))))

(defn mdb-disconnect
  "Close an existing connection to a MongoDB cluster"
  [^MongoClient conn]
  (.close conn))

(defn- mdb-exec-command
  "Helper function for mdb-run-command. Mainly useful in case we have
   to try and re-run the command from the error handler in mdb-run-command"
  [db command readPreference]
  ;;(println "Running command " command " on db " db " with readPreference " readPreference)
  (if (nil? readPreference)
                     (.runCommand db (pcv/to-bson-document command))
                     (.runCommand db (pcv/to-bson-document command) readPreference)))

(defn mdb-run-command
  "Run a MongoDB command against the database <db-name> and return the result
   as a map"
  [^MongoClient conn ^String dbname command & { :keys [ readPreference ] :or { readPreference nil } }]
  ;;(println "Running command " command " on connection " conn)
  (let [db  (.getDatabase conn dbname)]
    (try
      (pcv/from-bson-document (mdb-exec-command db command readPreference) true)
      (catch MongoCommandException e
        ;;(println "Received exception " e)
        (if (= (.getErrorCode e) 211)
          (do ;;(println "Received 'KeyNotFound' exception, retrying command " command " once more on database " db)
              (Thread/sleep 150)
              (let [result (mdb-exec-command db command readPreference)]
                ;;(println "Result of retried call is " result)
                (pcv/from-bson-document result true)))
             ;; (let [converted (pcv/from-bson-document result true)]
             ;;   (println "Converted result is " converted)
             ;;   converted)))
          (do ;;(println "Received MongoCommandException " e)
              (throw e)))))))

(defn mdb-admin-command
  "Run a MongoDB command against the admin database"
  [^MongoClient conn command & { :keys [ readPreference ] :or { readPreference nil }}]
  ;;(println "Running admin command " command " on connection " conn)
  (let [result (mdb-run-command conn "admin" command :readPreference readPreference)]
    ;;(println "Returning result " result)
    result))

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
