# Vagrant setup for testing the remote control functionality

The Vagrant setup in this directory is used for executing the remote tests in the mdb-runtime-tester test suite.

*Note that this is not a recommended MongoDB setup using Vagrant, but specific to this test environment*

## Setup

This vagrant configuration expects the following Vagrant plugins, otherwise it will not work:

- vagrant-disksize
- vagrant-hosts
- vagrant-vbguest

The vagrant-cachier plugin is not required, but recommended.

## Usage

### Prepping the test environment

Before you can execute the tests, you need to go through the following steps to get the test environment up an running:

- `vagrant up` to create the environment. This creates the VMs, installs the necessary software and creates the ssh keys that you will need to use from the 'testdriver' VM for logging into the replica set VMs
- Once all the VMs are up, use `ssh-copy-id` to copy the SSH key from 'testdriver' to rs1,rs2 and rs3.
- Start the mongod process on each VM using the supplied `start-mongod-replset-node.sh`
- If the replica set has not been initialised - ie, if you just provisioned the replica set - run the `init-replicaset.js` script from the mongo shell on testdriver
- Verify that the replica set has been initialised correctly by running rs.status() from the mongo shell on testdriver
- Start up the ssh-agent on testdriver as the remote code for the test suite relies on ssh-agent for remote logins
- Ensure that the environment variables printed out by ssh-agent are set
- Run `ssh-add` to add the default keys to ssh-agent

### Running the tests

The testdriver VM mounts the host's mdb-runtime-tester directory by default under ~/git-repo/mdb-runtime-tester, so it is not necessary to copy or clone any part of the git repo into the VM.

- Ensure that ssh-agent is running, its environment variables set and that you ssh-add'd the default ssh keys
- Run `lein test prototyping.ssh-test-sharded` in mdb-runtime-tester/prototyping. On the first invocation of lein, this will also download the actual leiningen executable and then build and run the remote tests only. Currently the VMs are not set up to execute any of the tests that require local MongoDB replica sets and sharded clusters, so a simple `lein test` will result in a lot of test failures
- All tests in the prototyping.ssh-test-sharded namespace should succeed.
- I recommend using tmux or screen to keep a permanent environment on the test driver box. The code for remote restarts of mongod/s requires ssh access, so you want to make sure that you have the correct settings in the environment and don't need to reset the environment every time you ssh into the testdriver vagrant box.
