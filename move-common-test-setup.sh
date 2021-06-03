#!/bin/sh

mkdir ssh-test-setup-common

git mv ssh-test-setup/common-functions.rb ssh-test-setup-common/common-functions.rb
git mv ssh-test-setup/create-test-user-x509.js ssh-test-setup-common/create-test-user-x509.js
git mv ssh-test-setup/create-test-user.js ssh-test-setup-common/create-test-user.js
git mv ssh-test-setup/init-replicaset-ssl.js ssh-test-setup-common/init-replicaset-ssl.js
git mv ssh-test-setup/init-replicaset-x509.js ssh-test-setup-common/init-replicaset-x509.js
git mv ssh-test-setup/init-replicaset.js ssh-test-setup-common/init-replicaset.js
git mv ssh-test-setup/mongod-service.conf ssh-test-setup-common/mongod-service.conf
git mv ssh-test-setup/mongod-ssh-rs.conf ssh-test-setup-common/mongod-ssh-rs.conf
git mv ssh-test-setup/mongod-ssh-ssl.conf ssh-test-setup-common/mongod-ssh-ssl.conf
git mv ssh-test-setup/mongod-ssh-x509-noauth.conf ssh-test-setup-common/mongod-ssh-x509-noauth.conf
git mv ssh-test-setup/mongod-ssh-x509.conf ssh-test-setup-common/mongod-ssh-x509.conf
git mv ssh-test-setup/start-mongod-replset-node.sh ssh-test-setup-common/start-mongod-replset-node.sh
git mv ssh-test-setup/test-keyfile ssh-test-setup-common/test-keyfile

cd ssh-test-setup

ln -s ../ssh-test-setup-common/common-functions.rb common-functions.rb
ln -s ../ssh-test-setup-common/create-test-user-x509.js create-test-user-x509.js
ln -s ../ssh-test-setup-common/create-test-user.js create-test-user.js
ln -s ../ssh-test-setup-common/init-replicaset-ssl.js init-replicaset-ssl.js
ln -s ../ssh-test-setup-common/init-replicaset-x509.js init-replicaset-x509.js
ln -s ../ssh-test-setup-common/init-replicaset.js init-replicaset.js
ln -s ../ssh-test-setup-common/mongod-service.conf mongod-service.conf
ln -s ../ssh-test-setup-common/mongod-ssh-rs.conf mongod-ssh-rs.conf
ln -s ../ssh-test-setup-common/mongod-ssh-ssl.conf mongod-ssh-ssl.conf
ln -s ../ssh-test-setup-common/mongod-ssh-x509-noauth.conf mongod-ssh-x509-noauth.conf
ln -s ../ssh-test-setup-common/mongod-ssh-x509.conf mongod-ssh-x509.conf
ln -s ../ssh-test-setup-common/start-mongod-replset-node.sh start-mongod-replset-node.sh
ln -s ../ssh-test-setup-common/test-keyfile test-keyfile

cd ../ssh-test-setup-ubuntu

rm common-functions.rb
rm create-test-user-x509.js
rm create-test-user.js
rm init-replicaset-ssl.js
rm init-replicaset-x509.js
rm init-replicaset.js
rm mongod-service.conf
rm mongod-ssh-rs.conf
rm mongod-ssh-ssl.conf
rm mongod-ssh-x509-noauth.conf
rm mongod-ssh-x509.conf
rm start-mongod-replset-node.sh
rm test-keyfile

ln -s ../ssh-test-setup-common/common-functions.rb common-functions.rb
ln -s ../ssh-test-setup-common/create-test-user-x509.js create-test-user-x509.js
ln -s ../ssh-test-setup-common/create-test-user.js create-test-user.js
ln -s ../ssh-test-setup-common/init-replicaset-ssl.js init-replicaset-ssl.js
ln -s ../ssh-test-setup-common/init-replicaset-x509.js init-replicaset-x509.js
ln -s ../ssh-test-setup-common/init-replicaset.js init-replicaset.js
ln -s ../ssh-test-setup-common/mongod-service.conf mongod-service.conf
ln -s ../ssh-test-setup-common/mongod-ssh-rs.conf mongod-ssh-rs.conf
ln -s ../ssh-test-setup-common/mongod-ssh-ssl.conf mongod-ssh-ssl.conf
ln -s ../ssh-test-setup-common/mongod-ssh-x509-noauth.conf mongod-ssh-x509-noauth.conf
ln -s ../ssh-test-setup-common/mongod-ssh-x509.conf mongod-ssh-x509.conf
ln -s ../ssh-test-setup-common/start-mongod-replset-node.sh start-mongod-replset-node.sh
ln -s ../ssh-test-setup-common/test-keyfile test-keyfile
