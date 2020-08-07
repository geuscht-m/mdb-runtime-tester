#!/bin/bash

mongod -f mongod-ssh-rs.conf
mongod -f mongod-ssh-ssl.conf
mongod -f mongod-ssh-x509-noauth.conf
