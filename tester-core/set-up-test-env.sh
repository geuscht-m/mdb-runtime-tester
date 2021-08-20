#!/bin/bash
# Script to set up the test environment.
# Requires that mtools/mlauch is available
# Creates three distinct test environments
#
# 1. Single, standalone mongod
# 2. Replica set with five nodes
# 3. Sharded cluster with 3x3 nodes
#
# All of the above are created in $HOME/tmp/mdb-test/
#
rm -rf $HOME/tmp/mdb-test

#mkdir -p $HOME/tmp/mdb-test/single
mkdir -p $HOME/tmp/mdb-test/replica
mkdir -p $HOME/tmp/mdb-test/sharded

# mlaunch init --single --dir $HOME/tmp/mdb-test/single
# mlaunch stop --dir $HOME/tmp/mdb-test/single
# sleep 5s

mlaunch init --replicaset --nodes 5 --dir $HOME/tmp/mdb-test/replica --wiredTigerCacheSizeGB 0.25
sleep 10s
mongo "mongodb://localhost/?replicaSet=replset" config-timeouts.js
sleep 5s
mlaunch stop --dir $HOME/tmp/mdb-test/replica

sleep 10s

mlaunch init --replicaset --nodes 3 --shards 3 --config 3 --dir $HOME/tmp/mdb-test/sharded --wiredTigerCacheSizeGB 0.25
sleep 30s
mongo "mongodb://localhost:27018,localhost:27019,localhost:27020/?replicaSet=shard01" config-timeouts.js
mongo "mongodb://localhost:27021,localhost:27022,localhost:27023/?replicaSet=shard02" config-timeouts.js
mongo "mongodb://localhost:27024,localhost:27025,localhost:27026/?replicaSet=shard03" config-timeouts.js
sleep 5s
mlaunch stop --dir $HOME/tmp/mdb-test/sharded
