# Lab 2: Primary-Backup Service
*Adapted from the [MIT 6.824
Labs](http://nil.csail.mit.edu/6.824/2015/labs/lab-2.html)*


## Introduction
In the previous lab, you guaranteed exactly-once execution of RPCs despite
unreliable network conditions. Your server maintained state, but it was not
fault-tolerant. Lab 2 is a first step towards fault tolerance for services with
state.


### Road Map for Labs 2-4
In the next 3 labs you will build several systems for replicating stateful
services. The labs differ in the degree of fault tolerance and performance they
provide:

* Lab 2 uses primary/backup replication, assisted by a view service that decides
  which machines are alive. The view service allows the primary/backup service
  to work correctly in the presence of network partitions. The view service
  itself is not replicated and is a single point of failure.
* Lab 3 uses the Paxos protocol for replication with no single point of failure
  and handles network partitions correctly. This service is slower than a
  non-replicated server would be, but is fault tolerant.
* Lab 4 is a sharded, transactional key/value database, where each shard
  replicates its state using Paxos. This key/value service can perform
  operations in parallel on different shards, allowing it to support
  applications that can put a high load on a storage system. Lab 4 has a
  replicated configuration service, which tells the shards for what key range
  they are responsible. It can change the assignment of keys to shards, for
  example, in response to changing load. It also supports transactions, allowing
  atomic update of keys held on different shards. Lab 4 has the core of a
  real-world design for thousands of servers.

In each lab you will have to do substantial design. We give you a sketch of the
overall design, but you will have to flesh it out and nail down a complete
protocol. The tests explore your protocol's handling of failure scenarios as
well as general correctness and basic performance. You may need to re-design
(and thus re-implement) in light of problems exposed by the tests, or even by
future labs; careful thought and planning may help you avoid too many re-design
cycles. We don't give you a description of the test cases (other than the code);
in the real world, you would have to come up with them yourself.


### Overview of Lab 2
In this lab you'll make a fault-tolerant service using a form of primary/backup
replication. In order to ensure that all parties (clients and servers) agree on
which server is the primary, and which is the backup, we'll introduce a kind of
master server, called the `ViewServer`. The `ViewServer` monitors whether each
available server is alive or dead. If the current primary or backup becomes
dead, the `ViewServer` selects a server to replace it. A client checks with the
`ViewServer` to find the current primary. The servers cooperate with the
`ViewServer` to ensure that at most one primary is active at a time.

Your service will allow replacement of failed servers. If the primary fails, the
`ViewServer` will promote the backup to be primary. If the backup fails, or is
promoted, and there is an idle server available, the `ViewServer` will cause it
to be the backup. The primary will send its complete application state to the
new backup, and then send subsequent operations to the backup to ensure that the
backup's application remains identical to the primary's.

It turns out the primary must send read operations (`Get`) as well as write
operations (Puts/Appends) to the backup (if there is one), and must wait for the
backup to reply before responding to the client. This helps prevent two servers
from acting as primary (a "split brain"). An example: S1 is the primary and S2
is the backup. The `ViewServer` decides (incorrectly) that S1 is dead, and
promotes S2 to be primary. If a client thinks S1 is still the primary and sends
it an operation, S1 will forward the operation to S2, and S2 will reply with an
error indicating that it is no longer the backup (assuming S2 obtained the new
view from the `ViewServer`). S1 can then return an error to the client
indicating that S1 might no longer be the primary (reasoning that, since S2
rejected the operation, a new view must have been formed); the client can then
ask the `ViewServer` for the correct primary (S2) and send it the operation.

Servers fail by crashing. That is, they can fail permanently but not restart.
However, other servers (including the `ViewServer`) do not have access to a
reliable failure detector. There is no way to distinguish between a failed
server and one which is temporarily unavailable.

The design outlined in the lab has some fault-tolerance and performance
limitations:

* The `ViewServer` is vulnerable to failures, since it's not replicated.
* The primary and backup must process operations one at a time, limiting their
  performance.
* A recovering server must copy the complete application state from the primary,
  which will be slow, even if the recovering server has an almost-up-to-date
  copy of the data already (e.g. only missed a few minutes of updates while its
  network connection was temporarily broken).
* The servers don't store the application data on disk, so they can't survive
  simultaneous crashes.
* If a temporary problem prevents primary to backup communication, the system
  has only two remedies: change the view to eliminate the backup, or keep
  trying; neither performs well if such problems are frequent.
* If a primary fails before acknowledging the view in which it is primary, the
  `ViewServer` cannot make progress---it will spin forever and not perform a
  view change. This will be explained in more detail in part 1.

We will address these limitations in later labs by using better designs and
protocols. This lab will make you understand what the tricky issues are so that
you can design better design/protocols. Also, parts of this lab's design (e.g.,
a separate `ViewServer`) are uncommon in practice.

The primary/backup scheme in this lab is not based on any published protocol. In
fact, this lab doesn't specify a complete protocol; you must flesh out the
protocol. The protocol has similarities with [Flat Datacenter
Storage](https://www.usenix.org/system/files/conference/osdi12/osdi12-final-75.pdf)
(the `ViewServer` is like FDS's metadata server, and the primary/backup servers
are like FDS's tractservers), though FDS pays far more attention to performance.
It's also a bit like a MongoDB replica set (though MongoDB selects the leader
with a Paxos-like election). For a detailed description of a (different)
primary-backup-like protocol, see [Chain
Replication](http://www.cs.cornell.edu/home/rvr/papers/osdi04.pdf). Chain
Replication has higher performance than this lab's design, though it assumes
that the `ViewServer` never declares a server dead when it is merely
partitioned. See [Harp](http://www.pmg.csail.mit.edu/papers/harp.pdf) and
[Viewstamped Replication](http://pmg.csail.mit.edu/papers/vr-revisited.pdf) for
a detailed treatment of high-performance primary/backup and reconstruction of
system state after various kinds of failures.


### A Note About Search Tests
Now that the solutions are more complex, you may find your code failing either
the correctness or the liveness search tests.

If your code fails a correctness test, the next step is relatively easy: the
checker provides you the counterexample, and you can use the debugger to
visualize it. However, note that our model checking is incomplete – your code
might have an error but we don't find it. For example, the sequence of messages
that triggers the problem may be longer than our search depth. This is a
fundamental problem for these types of tests.

The liveness tests have the opposite problem: if they pass then your code can
produce the right result, but they will flag an error if they can't find a valid
event sequence in the time allowed. This is also a fundamental problem for these
types of tests. It is why we include both correctness and liveness tests – you
have more assurance if you pass both than if you pass only one. For example, the
null solution that does nothing can sometimes pass a correctness test – it
doesn't do anything wrong! – but it will not pass the liveness test. Similarly,
passing a liveness test alone doesn't imply that your solution is bug free.

If your code does flag a liveness error, here are some steps to take:
1) `./run-tests --checks` will check that your handlers are deterministic, etc.
   If not, try fixing those issues first and rerunning the tests. By allowing
   faster search, this may also allow the model checker to find a liveness
   example, but it may also allow it to find additional correctness
   counterexamples. That's a good thing, in our view.
2) If you believe your solution is live, you can use the visual debugger to
   create by hand a sequence of messages that achieves the stated goal. You may
   find doing that that your code doesn't behave as you expected, ie., that
   there is a bug you need to fix.


## Part 1: The View Server
First you'll implement a view service (`ViewServer`) and make sure it passes our
tests; in Part 2 you'll build the `PBServer`. Your `ViewServer` won't itself be
replicated, so it will be relatively straightforward. Part 2 is much harder than
part 1, because the primary-backup service is replicated, and you have to flesh
out the replication protocol.

The `ViewServer` goes through a sequence of numbered views, each with a primary
and (if possible) a backup. A view consists of a view number and the identity of
the view's primary and backup servers.

Valid views have a few properties that are enforced:
* The primary in a view must always be either the primary or the backup of the
  previous view. This helps ensure that the key/value service's state is
  preserved. An exception: when the `ViewServer` first starts, it should accept
  any server at all as the first primary.
* The backup in a view can be any server (other than the primary), or can be
  altogether missing if no server is available (i.e., null).

These two properties -- a view can have no backup and the primary from a view
must be either the primary or backup of the previous view -- lead to the view
service being stuck if the primary fails in a view with no backup. This is a
flaw of the design of the `ViewServer` that we will fix in later labs.

Each key/value server should send a `Ping` message once per `PING_MILLIS` (use
`PingTimer` for this purpose on the `PBServer`s). The `ViewServer` replies to
the `Ping` with a `ViewReply`. A `Ping` lets the `ViewServer` know that the
server is alive; informs the server of the current view; and informs the
`ViewServer` of the most recent view that the server knows about. The ViewServer
should use the `PingCheckTimer` for deciding whether a server is alive or
(potentially) dead. If the ViewServer doesn't receive a Ping from a server
in-between two consecutive `PingCheckTimer`s, it should consider the server to
be dead. **Important:** to facilitate search tests, you should **not** store
timestamps in your `ViewServer`; your message and timer handlers should be
**deterministic**.

The `ViewServer` should return `STARTUP_VIEWNUM` with `null` primary and backup
when it has not yet started a view and use `INITIAL_VIEWNUM` for the first
started view. It then proceeds to later view numbers sequentially. The view
service can proceed to a new view in one of two cases:
1. It hasn't received a `Ping` from the primary or backup between two
   consecutive `PingCheckTimer`s.
2. There is no backup and there's an idle server (a server that's been pinging
   but is neither the primary nor the backup).

An important property of the `ViewServer` is that it will not change views
(i.e., return a different view to callers) until the primary from the current
view acknowledges that it is operating in the current view (by sending a `Ping`
with the current view number). If the `ViewServer` has not yet received an
acknowledgment for the current view from the primary of the current view, the
`ViewServer` should not change views even if it thinks that the primary or
backup has died.

The acknowledgment rule prevents the `ViewServer` from getting more than one
view ahead of the servers. If the `ViewServer` could get arbitrarily far ahead,
then it would need a more complex design in which it kept a history of views,
allowed servers to ask about old views, and garbage-collected information about
old views when appropriate. The downside of the acknowledgment rule is that if
the primary fails before it acknowledges the view in which it is primary, then
the `ViewServer` cannot change views, spins forever, and cannot make forward
progress.

It is important to note that servers may not immediately switch to the new view
returned by the `ViewServer`. For example, S1 could continue sending `Ping(5)`
even if the `ViewServer` returns view 6. This indicates that the server is not
ready to move into the new view, which will be important for Part 1 of this lab.
The `ViewServer` should not consider the view to be acknowledged until the
primary sends a ping with the view number (i.e., S1 sends `Ping(6)`).

You should not need to implement any messages, timers, or other data structures
for this part of the lab. Your `ViewServer` should have handlers for `Ping` and
`GetView` messages (replying with a `ViewReply` for each, where `GetView` simply
returns the current view without the sender "pinging") and should handle and set
`PingCheckTimer`s.

Our solution took approximately 100 lines of code.

You should pass the part 1 tests before moving on to part 2; execute them with
`run-tests.py --lab 2 --part 1`.


### Hints
* There will be some states that your `ViewServer` cannot get out of because of
  the design of the view service. For example, if the primary fails before
  acknowledging the view in which it is the primary. This is expected. We will
  fix these flaws in the design in future labs.
* You'll want to add field(s) to `ViewServer` in order to keep track of which
  servers have pinged since the second-most-recent `PingCheckTimer`; you'll need
  to differentiate between servers which have pinged since the most recent
  `PingCheckTimer` and servers which have not.
* Add field(s) to `ViewServer` to keep track of the current view.
* There may be more than two servers sending `Ping`s. The extra ones (beyond
  primary and backup) are volunteering to be backup if needed. You'll want to
  track these extra servers as well in case one of them needs to be promoted to
  be the backup.


## Part 2: The Primary/Backup Key/Value Service
Next, you will implement the client and primary-backup servers (`PBClient` and
`PBServer`).

Your service should continue operating correctly as long as there has never been
a time at which no server was alive. It should also operate correctly with
partitions: a server that suffers temporary network failure without crashing, or
can talk to some computers but not others. If your service is operating with
just one server, it should be able to incorporate an idle server (as backup), so
that it can then tolerate another server failure.

Correct operation means that operations are executed linearizably. All
operations should provide exactly-once semantics as in lab 1.

You should assume that the `ViewServer` never halts or crashes.

It's crucial that only one primary be active at any given time. You should have
a clear story worked out for why that's the case for your design. A danger:
suppose in some view S1 is the primary; the `ViewServer` changes views so that
S2 is the primary; but S1 hasn't yet heard about the new view and thinks it is
still primary. Then some clients might talk to S1, and others talk to S2, and
not see each other's operations.

A server that isn't the active primary should either not respond to clients, or
respond with an error.

A server should not talk to the `ViewServer` for every operation it receives,
since that would put the `ViewServer` on the critical path for performance and
fault-tolerance. Instead servers should `Ping` the `ViewServer` periodically
(once every `PING_MILLIS`) to learn about new views. Similarly, the client
should not talk to the `ViewServer` for every operation it sends; instead, it
should cache the current view and only talk to the `ViewServer` (by sending a
`GetView` message) on initial startup, when the current primary seems to be dead
(i.e., on `ClientTimer`), or when it receives an error.

When servers startup initially, they should `Ping` the `ViewServer` with
`ViewServer.STARTUP_VIEWNUM`. After that, they should `Ping` with the latest
view number they've seen, unless they're the primary for a view that has not yet
started.

Part of your one-primary-at-a-time strategy should rely on the `ViewServer` only
promoting the backup from view `i` to be primary in view `i+1`. If the old
primary from view `i` tries to handle a client request, it will forward it to
its backup. If that backup hasn't heard about view `i+1`, then it's not acting
as primary yet, so no harm done. If the backup has heard about view `i+1` and is
acting as primary, it knows enough to reject the old primary's forwarded client
requests.

You'll need to ensure that the backup sees every update to the application in
the same order as the primary, by a combination of the primary initializing it
with the complete application state and forwarding subsequent client operations.
The at-most-once semantics of `AMOApplication` (which you should once again wrap
your application in) should handle the backup receiving duplicate operations
from the primary. However, you will need to keep some state on the primary to
ensure that the backup processes operations in the correct order.

You will have to define your own messages and timers for this part of the lab.

Our solution took approximately 200 lines of code.

You should pass the part 2 tests; execute them with `run-tests.py --lab 2 --part
2`.


### Hints
* You'll probably need to create new messages and timers to forward client
  requests from primary to backup, since the backup should reject a direct
  client request but should accept a forwarded request.
* You'll probably need to create new messages and timers to handle the transfer
  of the complete application state from the primary to a new backup. You can
  send the whole application in one message.
* Even if your `ViewServer` passed all the tests in Part 1, it may still have
  bugs that cause failures in Part 2.
* Your `PBClient` should be very similar to the `SimpleClient` from lab 1.
