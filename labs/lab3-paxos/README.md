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
operations. Replicas that were not in the majority can catch up later, getting
the operations that they missed.


## Overview
Your system will consist of `PaxosServer`s and `PaxosClient`s. The clients send
`PaxosRequest`s to the servers, and servers respond with `PaxosReply`s. Each
node has the list of servers in the system. You are only provided the
aforementioned messages as well as the `ClientTimer`; you will have to define
the rest of the messages and timers your implementation uses.

Your system should guarantee *linearizability* of clients' commands. That is,
from the perspective of the callers of clients' functions and the results they
see, your implementation should be indistinguishable from a single, correct
entity processing clients' commands in sequence (e.g., your `SimpleServer` from
lab 1). Furthermore, your implementation should be able to process incoming
commands and return results as long as a majority of servers can communicate
with each other with "reasonable" message delay (and as long as the client can
send a command to these servers). This means that it should be robust to dropped
messages, as long as network connectivity is eventually restored.

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

You might find the Paxos lecture notes, [Paxos Made
Simple](https://lamport.azurewebsites.net/pubs/paxos-simple.pdf), and [Paxos
Made Moderately
Complex](http://www.cs.cornell.edu/courses/cs7412/2011sp/paxos.pdf), and
[Viewstamped Replication](https://dl.acm.org/citation.cfm?id=62549) useful.

We suggest you follow the Paxos Made Moderately Complex (PMMC) protocol.
However, you should note that in PMMC, nodes in Paxos are divided into different
"roles" (e.g., replica, acceptor, leader); your implementation only has one
role, `PaxosServer`, which will play all of the aforementioned roles
simultaneously. This could be done by keeping all of the state for each sub-role
entirely separate, but you'll find that there are opportunities for optimization
on the naive approach. You should not try to "spawn" scouts or commanders;
instead, simply keep the necessary state on your `PaxosServer`.


## Stable Leaders
In addition to the base PMMC protocol, you should implement a mechanism to find
a stable leader (sometimes called the distinguished proposer). When a leader is
preempted (receives a ballot larger than its own), instead of immediately
starting phase 1 of Paxos (spawning a scout) again, it should transition to
"follower mode" and stay inactive.

Every `PaxosServer` should have a `HeartbeatCheckTimer` which ticks, firing
periodically, exactly like the `PingCheckTimer` in lab 2. While in follower
mode, if a node sees two of these timers in a row without receiving a message
from the node it thinks is the active leader (the one with the largest ballot
it's seen), it should then attempt to become active and start phase 1 of Paxos.

To prevent itself from being preempted unnecessarily, while an active leader, a
`PaxosServer` should periodically broadcast `Heartbeat` messages to the other
nodes. Simply have another timer which fires periodically.

While you can use the AIMD (additive increase, multiplicative decrease) like the
one described in section 3 of the PMMC paper, we recommend keeping the timer
lengths fixed for simplicity. The timer lengths from lab 2 are a good starting
point, but you are free to tune them as you see fit.

One important note: this approach to leader election should only be seen as a
performance optimization. There could still be nodes which simultaneously
believe themselves to be an active leader – who believe their ballot is
acceptable to a majority. The Paxos protocol ensures safety in this case.


## Garbage Collection
A long-running Paxos deployment must forget about log slots that are no longer
needed and free the memory storing information about those slots. While PMMC
proposes one mechanism for garbage collection, for this lab we will do something
slightly simpler.

For our purposes, we will say that a command in a log slot is needed if it has
not been processed on all servers; it is not okay to delete commands as soon as
they are processed on the local server. In order to implement this log
compaction, each server will have to inform the other servers about the latest
point in its own log that is "stable." The easiest approach is to piggyback this
information on the periodic heartbeat protocol. Each follower server responds to
heartbeats with a message containing the latest slot they have executed
(`slot_out` in PMMC terms). Then, the active leader should be able to figure out
the latest log slot which has been executed on all nodes; it can then include
that information in subsequent heartbeats. Once a node learns that all nodes
have executed all slots up to some slot `i`, it can then safely discard all
information for slots less than or equal to `i`.

If one of your servers falls behind (i.e. does not receive the decision for some
instance), it will later need to find out what (if anything) was agreed to. A
simple way to bring a follower node up to date is by having the active leader
send it missing decisions when the follower sends its latest executed slot in
the above protocol.

Your garbage collection mechanism should be able to free memory from old log
slots when all Paxos servers can communicate with each other; it does not need
to make progress when only a majority can communicate. This is one weakness of
the simplified approach we describe. It is possible to do garbage collection of
slots that only a majority have executed and discovered the values for. In that
case, however, bringing lagging nodes up-to-date requires a complete *state
transfer*, which can get tricky. Additionally, doing state transfer is not as
modular; you will see what we mean by that when you use Paxos as a part of a
larger protocol in lab 4.


## Paxos Interface Methods
In order for the tests to more efficiently check your implementation, you'll
need to implement four methods in your `PaxosServer`: `status(logSlotNum)`,
`command(logSlotNum)`, `firstNonCleared()`, and `lastNonEmpty()`. These methods
simply return information about the *local state* of a server. Implementing
them should be straightforward, but be sure to pay attention their requirements
and implement them correctly.


## One-server Paxos
There is a test (worth 0 points) at the very end of the test suite called "Paxos
runs in singleton group" which validates that your Paxos implementation can run
with only one server and that it can process requests in a single step.
Ordinarily, a Paxos group with only one server isn't that useful. However, it
will be important in lab 4 that your Paxos implementation can function correctly
and efficiently when lab 4's search tests instantiate Paxos groups with a single
server. There should be very little difference between a single-server Paxos
group and your server from lab 1.


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
  labs; it should be able to handle duplicates of the same `Command` in the
  Paxos log. (Though you might want to try to keep them out as a performance
  optimization.)
* The easiest way for a client to send requests to the system is by broadcasting
  them to all servers.
* Your implementation needs to be able to handle "holes" in the Paxos log. That
  is, when completing the first phase of Paxos, a server might see previously
  accepted values for a slot but not previous slots. Your implementation should
  still make progress in this case.
* Figure out the minimum number of messages Paxos should use when reaching
  agreement in non-failure cases and make your implementation use that minimum.
* The search tests in this lab are rather minimal because the state space for
  most implementations of Paxos explodes quickly. You should take their passing
  with a grain of salt.
* You'll want to use the fact that the `Address` interface extends
  `Comparable<Address>` to implement ballots.
* One benefit of colocating all PMMC roles in the `PaxosServer` is that your
  "acceptor" knows both the accepted and decided values; you need not ever store
  more than one value for each slot on each node. You can then include both the
  accepted and decided values in P1B messages, speeding up the leader election
  process.
* You might find the `Multimap` classes from Guava useful for storing P2B
  messages for each slot.
* Non-static inner classes implicitly contain a reference to the enclosing
  object. If they are serialized, this reference will be serialized as well.
  This can cause your implementation to fail the garbage collection tests. You
  should prefer to make all inner classes you create static unless you have a
  good reason not to and you understand the implications.
