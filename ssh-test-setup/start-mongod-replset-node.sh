#!/bin/bash

mkdir -p $HOME/data
mongod --dbpath $HOME/data --fork --replSet test --logpath $HOME/data/mongod.log
