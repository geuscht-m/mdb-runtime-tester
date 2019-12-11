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

mkdir -p $HOME/tmp/mdb-test/single
mkdir -p $HOME/tmp/mdb-test/replica
mkdir -p $HOME/tmp/mdb-test/sharded

mlaunch init --single --dir $HOME/tmp/mdb-test/single
mlaunch stop --dir $HOME/tmp/mdb-test/single

mlaunch init --replicaset --nodes 5 --dir $HOME/tmp/mdb-test/replica
mlaunch stop --dir $HOME/tmp/mdb-test/replica

mlaunch init --replicaset --nodes 3 --shards 3 --config 3 --dir $HOME/tmp/mdb-test/sharded
mlaunch stop --dir $HOME/tmp/mdb-test/sharded
