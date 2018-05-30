# Paxos
*Adapted from the [MIT 6.824
Labs](http://nil.csail.mit.edu/6.824/2015/labs/lab-3.html)*


## Introduction
Lab 2 depends on a single master view server to pick the primary. If the view
server is not available (crashes or has network problems), then your service
won't work, even if both primary and backup are available. It also has the less
critical defect that it copes with a server (primary or backup) that's briefly
unavailable (e.g. due to a lost packet) by either blocking or declaring it dead;
the latter is very expensive because it requires a complete state transfer.

In this lab you'll fix the above problems by using Paxos to build a replicated
state machine. You won't have anything corresponding to a master view server.
Instead, a set of replicas will process all client requests in the same order,
using Paxos to agree on the order. Paxos will get the agreement right even if
some of the replicas are unavailable, or have unreliable network connections, or
even if subsets of the replicas are isolated in their own network partitions. As
long as Paxos can assemble a majority of replicas, it can process client
operations. Replicas that were not in the majority can catch up later by asking
Paxos for operations that they missed.


## Overview
Your system will consist of `PaxosServer`s and `PaxosClient`s. The clients send
`PaxosRequest`s to the servers, and servers respond with `PaxosReply`s. Each
node has the list of servers in the system. You are only provided the
aforementioned messages as well as the `ClientTimeout`; you will have to define
the rest of the messages and timeouts your implementation uses.

Your system should guarantee *linearizability* of clients' commands. That is,
from the perspective of the callers of clients' functions and the results they
see, your implementation should be indistinguishable from a single, correct
entity processing clients' commands in sequence. Furthermore, your
implementation should be able to process incoming commands and return results as
long as a majority of servers can communicate with each other with "reasonable"
message delay (and as long as the client can send a command to these servers).
This means that it should be robust to dropped messages, as long as network
connectivity is eventually restored.

You should achieve this by implementing the multi-instance Paxos algorithm.
Multi-instance Paxos can be viewed as a way for servers to agree on a shared log
of clients' commands. Agreement for each "slot" in the log is reached separately
by a different instance of Paxos, and servers can then play the log forward in
order, executing commands on their own local copy of the application, as long as
each command they execute is "stable" (i.e., agreement has been reached for that
command and all preceding commands). As long as no two servers ever decide
different values for a log slot (and as long as the underlying application is
deterministic), this approach will guarantee linearizability.

You *do not* need to implement server recovery. You should assume that servers
only fail by permanently crashing (or by being temporarily unreachable over the
network). You will, however, implement log compaction, a garbage collection
mechanism commonly used in practice to prevent the shared log from growing
without end and exhausting the memory on servers.

Your Paxos-based replicated state machine will have some limitations that would
need to be fixed in order for it to be a serious system. It won't cope with
crashes, since it stores neither the key/value database nor the Paxos state on
disk. Also, It requires the set of servers to be fixed, so one cannot replace
old servers. These problems can be fixed.

You should consult the Paxos lecture notes, [Paxos Made
Simple](https://lamport.azurewebsites.net/pubs/paxos-simple.pdf), and [Paxos
Made Moderately
Complex](http://www.cs.cornell.edu/courses/cs7412/2011sp/paxos.pdf), and
[Viewstamped Replication](https://dl.acm.org/citation.cfm?id=62549). However,
you should note that in many presentations, nodes in Paxos are divided into
different "roles" (e.g., proposer, acceptor); your implementation only has one
role, `PaxosServer`, which will play all of the aforementioned roles
simultaneously. This could be done by keeping all of the state for each sub-role
entirely separate, but you'll find that there are opportunities for optimization
on the naive approach.


## Garbage Collection
A long-running Paxos-based server must forget about log slots that are no longer
needed and free the memory storing information about those slots. A command in a
log slot is needed if it has not been processed on all servers; it is not okay
to delete commands as soon as they are processed on the local server. In order
to implement this log compaction, each server will have to inform the other
servers about the latest point in its own log that is "stable." It is okay for
you to piggyback this information in the agreement protocol messages or periodic
heartbeat messages. However, these messages should forward the log compaction
bookkeeping information for *all* servers, supporting a one–all–one
communication pattern.

If one of your servers falls behind (i.e. did not receive the decision for some
instance), it will later need to find out what (if anything) was agreed to.
There are several ways to do this. If you are using "durable leadership" (see
below), a reasonable way to do this would be to have the current leader send any
decisions it doesn't know that other servers know to those servers.

One utility function you'll likely want to implement on `PaxosServer` is
`maxDone()`:

```java
/**
 * Locally computes the maximum slot number which the server knows has been
 * executed on all PaxosServers.
 */
private int maxDone() {
}
```

Commands in log slots less than or equal to this value can be safely discarded.
You can then use the test `slot <= maxDone()` throughout your server code.

Your garbage collection mechanism should be able to free memory from old log
slots when all Paxos servers can communicate with each other; it does not need
to make progress when only a majority can communicate.


## Leadership
One of the biggest design decisions you'll have to make when implementing your
replicated state machine is when your servers will execute the first phase of
Paxos. Each server should initiate this first phase on startup, but what happens
once a node is preempted while proposing a value? It could immediately restart
the first Paxos phase. Or, it could instead assume that the preempting node is
now the "distinguished proposer" and wait until that node appears to be
unavailable (e.g., because it has not received a heartbeat/ping response/other
message from that node recently) before attempting to execute the first Paxos
phase again.

Note that in both cases, your system will still have to cope with multiple nodes
proposing values simultaneously. However, in the "durable leadership" case
(sometimes called Multi-Paxos), you're trying to make that less likely.

Both options are viable, though different in terms of efficiency, and this
choice will have ramifications for the rest of your implementation. For
instance, the case where you have a distinguished proposer is more amenable to
the common optimization wherein nodes execute the first Paxos phase for *all*
log slots simultaneously (instead of each log slot being managed completely
independently).


---


Our solution took approximately 400 lines of code.

You should pass the lab 3 tests; execute them with `./run-tests.py --lab 3`.


## Hints
* Your system should be able to reach agreement on different commands
  concurrently.
* Nodes should never send messages to themselves. You may wish to implement your
  own utility function on `PaxosServer` for sending a message to all servers
  while bypassing the network when sending to itself.
* Once a `PaxosServer` proposes a value in a slot, it should make sure that, as
  long as it is not preempted by another server and can communicate with a
  quorum, that value is eventually decided in that slot.
* Your implementation should make use of the `atmostonce` package as in previous
  labs.
* In order to implement log compaction properly, you might need to adjust the
  way your `AMOApplication` works. Remember, clients only have one outstanding
  `Request` at a time.
* Your implementation needs to be able to handle "holes" in the Paxos log. That
  is, at certain points, a server might see agreement being reached on a slot
  but not previous slots. Your implementation should still make progress in this
  case.
* Figure out the minimum number of messages Paxos should use when reaching
  agreement in non-failure cases and make your implementation use that minimum.
* The search tests in this lab are rather minimal because the state space for
  most implementations of Paxos explodes quickly. You should take their passing
  with a grain of salt.
* You'll want to use the fact that the `Address` interface extends
  `Comparable<Address>`.
