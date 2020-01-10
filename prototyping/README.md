# Test driver for application resilience tests

This project contains the test driver framework and associated tests to run database resilicence tests for your application that uses MongoDB.

## Installation

At this point in time, the project does not provide any pre-built binaries. To use these tools, please clone or fork the repository and build the binaries locally.

In order to build the binaries, you need the following tools:

- Reasonably recent Java runtime and JDK. The code is currently tested with OpenJDK 11 and OpenJDK 11, however it should run on older versions of the JDK also, likely from JDK 8 upwards.
- [Leiningen](https://leiningen.org/) as the build and dependency management tool

## Usage

### General usage

As of right now the best usage information can be found in tests the prototyping/test/prototyping directory. I recommend looking in the core_test-*.clj files for examples on local tests where the mongods interacted with reside on the local machine, and the ssh_test_*.clj files for tests that involve remote mongods and mongos. In general, you'll have to build a fairly small piece of Clojure code that executes the required actions and, if necessary, undoes them.

For example, this code snippet triggers an election on the replica set at mongodb://localhost:27017:

`(trigger-election "mongodb://localhost:27017")`

Here's another code snippet that degrades a replica set by shutting down a minority number of nodes, waits 30 seconds and then undoes the 'stop' action:

`(let [restart-cmd (make-rs-degraded "mongodb://localhost:27017") ]
      (Thread/sleep 30000)
      (restart-cmd))`

Note the assignment to `restart-cmd` - the `make-rs-degraded` function returns a closure that, when executed, undoes the actions taken to degrade the replica set. All the test functions that have undoable results follow this pattern.

I have some plans to support driving the basic tests through a declarative language - most likely YAML - but this is one of my "when I get around to it" items.

### Specific considerations for interacting with remote servers

The code for the test driver uses [clj-ssh](https://github.com/clj-commons/clj-ssh) to execute remote OS commands on replica set members. clj-ssh relies on ssh-agent to provide keys to log in to the remote servers. Note that the user associated with the ssh key needs to have the appropriate privileges on the target system to start and stop processes, specifically mongod and mongos. Most importantly, if you use the framework to shut down a remote mongod, the user you use to log into the remote system must have appropriate access rights so mongods and mongos started by it can read their configuration files and read/write to the database, log and journal paths.

## Current state and limitations

The very basic tests are usable, but require a fair amount of care. The project is under active development and in its early stages, so bugs are still features. Pull requests are welcome :).

Known limitations at the moment are:
- The restart code currently entirely relies on the command line that mongod returns as part of the server status. This means that under Linux OSs, the code currently can't distinguish between MongoDB processes that have been started manually or via script, and ones that have been started as services.
- Currently there is no way to configure the remote ssh user
- There is currently no support for ssh jump hosts

## Currently supported operating systems

The code is currently tested on Linux and macOS. I've not tested it on Windows and don't expect it to work on Windows as a target system due to its reliance on ssh for remote process execution. The driver should work from WSL but I have not tested that yet, either.

## Third party modules used

Shout out to the following projects that I use to build these tools. 

- [Clojure](http://clojure.org)
- [Leiningen](https://leiningen.org)
- [Monger](https://github.com/michaelklishin/monger), to talk to MongoDB from Clojure. Although in certain places I reach through straight to the Java driver
- [Urly](https://github.com/michaelklishin/urly) for URL parsingü
- [clj-ssh](https://github.com/clj-commons/clj-ssh) to interact with ssh for remote servers
- [clojure-jna](https://github.com/Chouser/clojure-jna/) for simple interaction with the C runtime library from Clojure
- [timbre](https://github.com/ptaoussanis/timbre) and [slf4j-timbre](https://github.com/fzakaria/slf4j-timbre) to interact with slf4j logging
