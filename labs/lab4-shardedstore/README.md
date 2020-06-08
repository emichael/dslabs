# Lab 4: Sharded Key/Value Service
*Adapted from the [MIT 6.824
Labs](http://nil.csail.mit.edu/6.824/2015/labs/lab-4.html)*


## Introduction
In this lab you'll build a linearizable key/value storage system that "shards"
(partitions) the keys over a set of replica groups and handles cross-group
transactions. In this lab, a shard is a subset of the key/value pairs; for
example, all the keys starting with "a" might be one shard, all the keys
starting with "b" another, etc. The reason for sharding is performance. Each
replica group handles puts and gets for just a few of the shards, and the groups
operate in parallel; thus total system throughput (puts and gets per unit time)
increases in proportion to the number of groups.

Your sharded key/value store will have two main components. First, a set of
replica groups. Each replica group is responsible for a subset of the shards; a
replica group consists of a set Paxos servers. The second component is the
*shard master*. The shard master decides which replica group should serve each
shard; this information is called the configuration. The configuration changes
over time. Clients consult the shard master in order to find the replica group
for a key, and replica groups consult the master in order to find out what
shards to serve. There is a single shard master for the whole system,
implemented as a fault-tolerant service using Paxos.

A sharded storage system must be able to shift shards among replica groups. One
reason is that some groups may become more loaded than others, so that shards
need to be moved to balance the load. Another reason is that replica groups may
join and leave the system: new replica groups may be added to increase capacity,
or existing replica groups may be taken offline for repair or retirement.

The main challenge in part 2 of this lab will be handling reconfiguration in the
replica groups. Within a single replica group, all group members must agree on
when a reconfiguration occurs relative to client `Put`/`Append`/`Get` requests.
For example, a `Put` may arrive at about the same time as a reconfiguration that
causes the replica group to stop being responsible for the shard holding the
`Put`'s key. All replicas in the group must agree on whether the `Put` occurred
before or after the reconfiguration. If before, the `Put` should take effect and
the new owner of the shard will see its effect; if after, the `Put` won't take
effect and client must re-try at the new owner. The recommended approach is to
have each replica group use Paxos to log not just the sequence of `Put`s,
`Append`s, and `Get`s but also the sequence of reconfigurations.

Reconfiguration also requires interaction among the replica groups. For example,
in configuration 10 group G1 may be responsible for shard S1. In configuration
11, group G2 may be responsible for shard S1. During the reconfiguration from 10
to 11, G1 must send the contents of shard S1 (the key/value pairs) to G2.

You will need to ensure that at most one replica group is serving requests for
each shard. Luckily it is reasonable to assume that each replica group is always
available, because each group uses Paxos for replication and thus can tolerate
some network and server failures. As a result, your design can rely on one group
to actively hand off responsibility to another group during reconfiguration.
This is simpler than the situation in primary/backup replication, where the old
primary is often not reachable and may still think it is primary.

In part 3 you will extend your key-value store to handle multi-key transactions.
When these transactions touch shards held by different replica groups, you will
use two-phase commit with locking to ensure linearizability of operations.

This lab's general architecture (a configuration service and a set of replica
groups) is patterned at a high level on a number of systems: Flat Datacenter
Storage, BigTable, Spanner, FAWN, Apache HBase, Rosebud, and many others. These
systems differ in many details from this lab, though, and are also typically
more sophisticated and capable. For example, your lab lacks persistent storage
for key/value pairs and for the Paxos log; it cannot evolve the sets of peers in
each Paxos group; its data and query models are very simple; and handoff of
shards is slow and doesn't allow concurrent client access.


## Part 1: The Shard Master
The `ShardMaster` manages a sequence of numbered configurations (starting with
`INITIAL_CONFIG_NUM`). Each configuration describes a set of replica groups and
an assignment of shards (numbered 1 to `numShards`) to replica groups. Whenever
this assignment needs to change, the shard master creates a new configuration
with the new assignment. Key/value clients and servers contact the `ShardMaster`
when they want to know the current (or a past) configuration.

The `ShardMaster` runs as an `Application` and will be replicated with your
implementation of Paxos, which means that as long as it is deterministic, it
will be fault-tolerant and guarantee linearizability and exactly-once semantics
for operations.

Your implementation must support the `Join`, `Leave`, `Move`, and `Query`
operations in `ShardMaster.java`.

`Join` contains a unique positive integer replica group identifier (`groupId`)
and set of server addresses. The `ShardMaster` should react by creating a new
configuration that includes the new replica group. The new configuration should
divide the shards as evenly as possible among the groups, and should move as few
shards as possible to achieve that goal. The `ShardMaster` should return `OK`
upon successful completion of a `Join` and `Error` if that group already exists
in the latest configuration.

`Leave` contains the `groupId` of a previously joined group. The `ShardMaster`
should create a new configuration that does not include the group, and that
assigns the group's shards to the remaining groups. The new configuration should
divide the shards as evenly as possible among the groups, and should move as few
shards as possible to achieve that goal. The `ShardMaster` should return `OK`
upon successful completion of a `Leave` and `Error` if that group did not exist
in the latest configuration.

`Move` contains a shard number and a `groupId`. The `ShardMaster` should create
a new configuration in which the shard is assigned to the group and only that
shard is moved from the previous configuration. The main purpose of `Move` is to
allow us to test your software, but it might also be useful to load balance if
some shards are more popular than others or some replica groups are slower than
others. A `Join` or `Leave` following a `Move` could undo a `Move`, since `Join`
and `Leave` re-balance. The `ShardMaster` should return `OK` upon successful
completion of a `Move` (one which actually moved the shard) and `Error`
otherwise (e.g., if the shard was already assigned to the group).

`Query` contains configuration number. The `ShardMaster` replies with a
`ShardConfig` object that has that configuration number. If the number is -1 or
larger than the largest known configuration number, the `ShardMaster` should
reply with the latest configuration. The result of `Query(-1)` should reflect
every `Join`, `Leave`, or `Move` that completed before the `Query(-1)` was sent.

The very first configuration, created when the first `Join` is successfully
executed, should be numbered `INITIAL_CONFIG_NUM`. Before this configuration is
created, the result of a `Query` should be `Error` instead of a `ShardConfig`
object.


Our solution to part 1 took approximately 200 lines of code.

You should pass the part 1 tests before moving on to part 2; execute them with
`run-tests.py --lab 4 --part 1`.


## Part 2: Sharded Key/Value Server, Reconfiguration
Now you'll build a sharded fault-tolerant key/value storage system.

Each `ShardStoreServer` will operate as part of a replica group. Each replica
group will serve operations for some of the key-space shards. Use `keyToShard()`
in `ShardStoreNode` to find which shard a key belongs to; you should use use
`SingleKeyCommand.key()` (all of the operations you'll handle in part 2 are
single-key operations) in `ShardStoreClient` to determine the key for a given
operation.

Multiple replica groups will cooperate to serve the complete set of shards. A
replicated `ShardMaster` service will assign shards to replica groups. When this
assignment changes, replica groups will have to hand off shards to each other.
Your storage system must provide linearizability of `KVStore` operations passed
to `ShardStoreClient`. This will get tricky when `Get`s, `Put`s, and `Append`s
arrive at about the same time as configuration changes.

You are allowed to assume that a majority of servers in each replica group are
alive and can talk to each other, can talk to a majority of the `ShardMaster`
servers, and can talk to a majority of every other replica group. Your
implementation must operate (serve requests and be able to re-configure as
needed) if a minority of servers in some replica group(s) are dead, temporarily
unavailable, or slow.

Your servers should not try to send `Join` operations to the `ShardMaster`. The
tests will send configuration changes when appropriate. `ShardStoreServer` and
`ShardStoreClient` should only send `Query`s to the `ShardMaster` servers.

Your `ShardStoreServer` should use Paxos to replicate operations among replicas
in the same replica group as follows: First, modify `PaxosServer` by adding
another constructor, which, instead of taking an application takes an `Address`.
When started this way, your `PaxosServer` should run exactly the same as before,
except instead of executing commands, it sends all decisions in order to the
given address (using `handleMessage` described below). Next, in
`ShardStoreServer` create a `PaxosServer` and initialize it as below.

```java
private static final String PAXOS_ADDRESS_ID = "paxos";
private Address paxosAddress;

public void init() {
    // Setup Paxos
    paxosAddress = Address.subAddress(address(), PAXOS_ADDRESS_ID);

    Address[] paxosAddresses = new Address[group.length];
    for (int i = 0; i < paxosAddresses.length; i++) {
      paxosAddresses[i] = Address.subAddress(group[i], PAXOS_ADDRESS_ID);
    }

    PaxosServer paxosServer =
        new PaxosServer(paxosAddress, paxosAddresses, address());
    addSubNode(paxosServer);
    paxosServer.init();

    ...
}
```

This sets up a Paxos group for each replica group. `ShardStoreServer`s can then
pass operations to their local Paxos node to be proposed by calling
`handleMessage(message, paxosAddress)`. Nodes within the same root node can pass
messages to each other through this interface; these messages are not sent over
the network but are immediately (and reliably) handled.

Your `ShardStoreServer`s should instantiate their own local `KVStore` (wrapped
in an `AMOApplication`). Unlike previous systems, this system will not be able
to handle different underlying `Application`s.

Finally, your `ShardStoreServer` and `ShardStoreClient` should periodically send
`Query` operations to the `ShardMaster`s to learn about new configurations. In
order to do this without creating sequence numbers for each `Query` sent, you
might want to modify `PaxosServer` to allow it to handle read-only non-AMO
commands, and send `Query`s (which are read-only) as simple `Command`s.


Our solution to part 2 took approximately 350 lines of code.

You should pass the part 2 tests; execute them with `run-tests.py --lab 4 --part
2`.


### Hints
- You should handle all communication between replicas in the same group through
  Paxos. Replicas should propose operations to the Paxos log, and they will all
  process them in the same order. This should include key-value operations and
  also any operations needed for reconfiguration. You should create your own
  sub-Interface of `Command` which all of your reconfiguration-specific
  operations inherit from so that you can easily propose these operations to the
  Paxos log.
- The easiest way for a replica/group to send a message to a different group is
  by broadcasting the message to the entire group.
- Your server should respond with an error message to a client operation on a
  key that the server isn't responsible for (i.e. for a key whose shard is not
  assigned to the server's group). As in the primary-backup case, the client can
  then go back to the `ShardMaster`s to learn about the latest configuration.
- Process re-configurations one at a time, in order.
- During re-configuration, replica groups will have to send each other the keys
  and values for some shards (you might have to modify `KVStore` to support
  this).
- Be careful about guaranteeing at-most-once semantics for key-value operations.
  When a server sends shards to another, the server needs to send
  `AMOApplication` state as well. Think about how the receiver of the shards
  should update its `AMOApplication` state.
- Think about when it is okay for a server to give shards to the other server
  during re-configuration.
- The majority of the search tests for this lab assume that your Paxos
  implementation is correct and only model check the new protocol. They do this
  by instantiating all Paxos groups with a single server. Your Paxos
  implementation should be able to reach agreement in a single step when there
  is only one server; there is a test at the end of lab 3 that validates this.
- You may have implemented optimizations in lab 3 by making assumptions which
  were valid but do not hold for this lab. In particular, you should be very
  cautious about dropping proposals when Paxos is running as a sub-node. As a
  sub-node, Paxos should be oblivious to `AMOApplication` logic and should be
  able to decide same command for different slots. Some de-duplication at the
  `PaxosServer` level is possible, but it must be done carefully.


## Part 3: Transactions
Finally, you'll extend your key-value store to support cross-group transactions
using two-phase commit.

First, you'll need to complete the `execute` method in `TransactionalKVStore` to
extend your key-value store to support the `Transaction` interface we define.
This should take one or just a few lines of code. You should then initialize
your `ShardStoreServer` with a `TransactionalKVStore` rather than a `KVStore`.

With those preliminaries out of the way, you'll then need to modify your server
to handle transactions, as well as your client to route transactions to the
correct server. You'll use two-phase commit with locking to ensure
serializability of transactions. In the first phase, one group, serving as
transaction coordinator, will send a prepare request; the other participants in
the transaction, upon receiving the prepare request, will acquire read and write
locks for the transaction and respond. If all groups participating in the
transaction respond and no group aborts, the transaction coordinator will send
the commit message to all groups; the groups will then free the locks and
acknowledge the commit message. As previously stated, you are free to assume
that a majority of servers from each group will remain active. Furthermore, if
any nodes do fail, they fail by crashing. You *do not* need to implement a
node failure recovery protocol.

Your system should guarantee linearizability of all transactions and be
deadlock-free; it should never reach a state where it cannot make progress.
Furthermore, it should always be able to process reconfigurations, and when
there are no ongoing reconfigurations are no conflicting transactions, it should
be able to make progress and commit transactions (as long as the consensus
protocol underlying each group continues to make progress, of course). You do
not need to guarantee fairness, however (more on this below).

You should think carefully about how transactions will interact and interleave
with reconfigurations. Unless handled with extreme care, attempting to execute
transactions with concurrent reconfigurations could lead to deadlock or
linearizability violations. Therefore, we suggest the following approach:
- All `ShardStoreServer` nodes tag their transaction-handling messages with
  their configuration number.
- Servers reject any prepare requests coming from different configurations,
  causing the transaction to abort.
- Servers *delay* reconfigurations when there are outstanding locks for keys
  (i.e., there are transactions pending in the previous configuration).

This means that any transaction will occur entirely in one configuration or
another. Even given that, however, you'll have to be careful to avoid deadlock.
The easiest way to avoid deadlock is to have your servers *reject any prepare
requests when they cannot acquire locks* and cause the transaction to abort.
This could lead to livelock if concurrent transactions continually cause each
other to abort. You can lessen the chance that this will happen, however, by
giving each group a fixed priority (for instance, using its group ID) and having
the clients always send their transactions to the group with highest priority
among transaction participants (and enforcing that choice server-side). While
eliminating transaction livelock and guaranteeing fairness among transactions
are not requirements for this lab, they are important properties in practice,
and you should think about how you would go about achieving them!


Our solution to part 3 took approximately 500 lines of code.

You should pass the part 3 tests; execute them with `run-tests.py --lab 4 --part
3`.


### Hints
* When a transaction gets aborted, it will need to be retried. You will need to
  be able to differentiate the prepare responses/aborts across different
  attempts to commit the same instance of a transaction.
* While you should assume that the test code always sends commands one-at-a-time
  (i.e., waits for a result before sending the next command), you may (or may
  not) have to go to greater lengths in this lab to ensure that transactions
  from the same client get processed in the same order on all replicas.


---


### Visual Debugging for Lab 4
The arguments to start the visual debugger for this lab are slightly different.
To start the visual debugger, execute `./run-tests.py -d NUM_GROUPS
NUM_SERVERS_PER_GROUP NUM_SHARDMASTERS NUM_CLIENTS CLIENT_WORKLOAD
[CONFIG_WORKLOAD]` where:
- `NUM_GROUPS` is the number of `ShardStoreServer` groups
- `NUM_SERVERS_PER_GROUP` is the number of `ShardStoreServer`s in each group
- `NUM_SHARDMASTERS` is the number of Paxos servers replicating the
  `ShardMaster`
- `NUM_CLIENTS` is the number of `ShardStoreClient`s
- `CLIENT_WORKLOAD` is the `ShardStoreClient`'s workload, a comma-separated list
  of `KVStoreCommand`s. These can include `GET`, `PUT`, and `APPEND`, which have
  the normal syntax, as well as transactions, which have the following syntax:
  - `MULTIGET:key1:key2:...:keyN`
  - `MULTIPUT:key1:value1:key2:value2:...:keyN:valueN`
  - `SWAP:key1:key2`
- `CONFIG_WORKLOAD` is an optional comma-separated list of commands for the
  `ShardMaster`s of the form `JOIN:groupId`, `LEAVE:groupId`, or
  `MOVE:groupId:shardNum`. The default is one `Join` for each group.

The default number of shards is 10, though this can be changed in
`ShardStoreVizConfig`.
