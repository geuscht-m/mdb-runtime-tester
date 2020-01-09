#!/bin/bash

#mkdir -p $HOME/data
mongod --dbpath $HOME/data --fork --replSet replTest --logpath $HOME/data/mongod.log --bind_ip_all --auth --keyFile $HOME/data/keyfile
