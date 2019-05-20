(stop-mongo-process 'localhost) ;; Works on mongos and mongod
(start-mongo-process 'localhost "test/mongodb.conf")
(stepdown-primary 'localhost)


(make-rs-read-only 'rs-uri)
(restart-random-rs-member 'rs-uri)
(trigger-election 'rs-uri)
(simulate-maintenance 'rs-uri)
(make-config-servers-read-only 'sharded-cluster-uri)
(make-shard-read-only 'shard-uri)
(make-random-shard-read-only 'shard-uri)
(make-cluster-read-only 'shard-uri)

(cause-random-chaos 'how-long)

