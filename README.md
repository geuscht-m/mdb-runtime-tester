A suite of tools to test your application's behaviour in light of events that can and do occur during normal production operations.

Note that this tooling is intended to test your application as it interacts with MongoDB, not MongoDB itself. The latter is already taken care of by MongoDB engineering as part of their release process.

The test cases supported are split into two different categories - normal MongoDB events like a primary stepping down followed by an election, and abnormal events like nodes becoming unavailable that may or may not affect the overall operation of the replica set.

## Normal events

These type of test cases cover events like the following:

- Primary stepping down, followed by the election of a new primary
- Rolling restart of a replica set as part of a configuration change or update/upgrade
- mongos are restarted
- One member of the replica set is running an initial sync
- A minority of nodes in a replica set or shard of a sharded cluster are unavailable. While this type of event can have a performance impact, the replica set or shard(s) remain available for writes.

What all these event types have in common are that you're likely to see them during normal operations in a healthy replica set. They're also the kind of even that you could consider zero downtime as the replica set or sharded cluster continues to function as designed, with the potential exception of a brief interruption in write capabilities. What these types of events have in common is that your application should be able to handle them without affecting the user experience.

## Extraordinary events

These types of events are more likely to affect the user experience of your application. Typical examples for this type of event are either having a noticeable impact on performance or prevent writes to a replica set or one or more shards on a sharded cluster. The big difference between this type of even and the "Normal Events" is that while these event types can be acknowledged by the application, they will require special treatment by your application. Typical examples of these type of events are:

- Replica set is read only as only the minority of nodes is available and can't form quorum.
- The replica set backing a single shard in a sharded cluster is read only, so the sharded cluster is partially read only
- Every replica set backing a sharded cluster is read only, rendering the whole cluster read only
- The replica set for the config servers is read only. While this doesn't affect the writeability of a sharded cluster directly, it prevents the sharded cluster from splitting and migrating chunks as the meta data can't be updated.

## Supported environments

The test components are currently tested on Linux and macOS. They are not currently tested on Windows and unlikely to work on Windows for now, as they use ssh to interact with remote servers.
