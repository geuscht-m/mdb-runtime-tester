;; NOTE - duplicated from my hacked monger fork - needed internally here
;;        replace when possible
;;
(ns tester-core.conv-helpers
   (:import [com.mongodb.client MongoCursor]
            [clojure.lang IPersistentMap Named Keyword Ratio]
            [java.util List Map Date Set]
            org.bson.Document
            org.bson.types.ObjectId
            org.bson.types.Binary
            (org.bson.types Decimal128)))


(defprotocol ConvertToBSONDocument
  (^org.bson.Document to-bson-document [input] "Converts given piece of Clojure data to the BSON Document the MongoDB Java driver uses"))

(extend-protocol ConvertToBSONDocument
  nil
  (to-bson-document [input]
    nil)

  String
  (to-bson-document [^String input]
    input)

  Boolean
  (to-bson-document [^Boolean input]
    input)

  java.util.Date
  (to-bson-document [^java.util.Date input]
    input)

  ;; org.joda.time.DateTime
  ;; (to-bson-document [^org.joda.time.DateTime input]
  ;;   (.toDate input))
  
  Ratio
  (to-bson-document [^Ratio input]
    (double input))

  Keyword
  (to-bson-document [^Keyword input] (.getName input))

  Named
  (to-bson-document [^Named input] (.getName input))

  IPersistentMap
  (to-bson-document [^IPersistentMap input]
    (let [o (Document.)]
      (doseq [[k v] input]
        (.put o (to-bson-document k) (to-bson-document v)))
      o))

  List
  (to-bson-document [^List input] (map to-bson-document input))

  Set
  (to-bson-document [^Set input] (map to-bson-document input))

  Document
  (to-bson-document [^Document input] input)

  com.mongodb.DBRef
  (to-bson-document [^com.mongodb.DBRef dbref]
    dbref)

  Object
  (to-bson-document [input]
    input))


(defprotocol ConvertFromBSONDocument
  (from-bson-document [input keywordize] "Converts given BSON Document instance to a piece of Clojure data"))

(extend-protocol ConvertFromBSONDocument
  nil
  (from-bson-document [input keywordize] input)

  Object
  (from-bson-document [input keywordize] input)

  Decimal128
  (from-bson-document [^Decimal128 input keywordize]
    (.bigDecimalValue input)
    )

  List
  (from-bson-document [^List input keywordize]
    (vec (map #(from-bson-document % keywordize) input)))

  ;; BasicDBList
  ;; (from-db-object [^BasicDBList input keywordize]
  ;;   (vec (map #(from-db-object % keywordize) input)))

  com.mongodb.DBRef
  (from-bson-document [^com.mongodb.DBRef input keywordize]
    input)

  ;; Binary
  ;; (from-bson-document [^Binary input keywordize]
  ;;   (println "Converting binary")
  ;;   input)
  
  Document
  (from-bson-document [^Document input keywordize]
    ;; DBObject provides .toMap, but the implementation in
    ;; subclass GridFSFile unhelpfully throws
    ;; UnsupportedOperationException.
    (reduce (if keywordize
              (fn [m ^String k]
                ;;(println "Type of input k is " (type (.get input k)))
                (assoc m (keyword k) (from-bson-document (.get input k) true)))
              (fn [m ^String k]
                (assoc m k (from-bson-document (.get input k) false))))
            {} (.keySet input))))
