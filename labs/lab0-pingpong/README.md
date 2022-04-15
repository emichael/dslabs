# Lab 0: Ping Server
This lab serves as a brief introduction to the interface you'll be programming
against, as well as the testing infrastructure. We'll take a look at a very
simple distributed system: a server which responds to pings and clients which
ping that server.


## Dramatis Personae
Let's start with the `PingApplication`. `Application`s in this framework are
simple state machines. They consume `Command`s, update internal state, and
return `Result`s. The `PingApplication` is quite a simple one.

```java
@ToString
@EqualsAndHashCode
public class PingApplication implements Application {
    @Data
    public static final class Ping implements Command {
        @NonNull private final String value;
    }

    @Data
    public static final class Pong implements Result {
        @NonNull private final String value;
    }

    @Override
    public Pong execute(Command command) {
        if (!(command instanceof Ping)) {
            throw new IllegalArgumentException();
        }

        Ping p = (Ping) command;

        return new Pong(p.value());
    }
}
```

It defines one `Command` (`Ping`) and one `Result` (`Pong`). Whenever it gets a
`Ping`, it returns a `Pong` with the same value. The `PingApplication` lives on
the `PingServer`.

```java
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PingServer extends Node {
    private final PingApplication app = new PingApplication();

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PingServer(Address address) {
        super(address);
    }

    @Override
    public void init() {
        // No initialization necessary
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private void handlePingRequest(PingRequest m, Address sender) {
        Pong p = app.execute(m.ping());
        send(new PongReply(p), sender);
    }
}
```

The `PingServer` is a `Node`—the basic unit of computation in a distributed
system. It holds a `PingApplication`, does nothing on initialization, and
defines a single message handler for the `PingRequest` message. That handler
simply passes the `Ping` the message contains to the `PingApplication`. Having a
`PingApplication` instead of handling the `Ping` directly on the server may seem
a little contrived, but it will make more sense in later labs when we build
systems which can support *any* application.

Speaking of messages, they're how your `Node`s speak to each other. This system
has two. They're pretty self-explanatory.

```java
@Data
class PingRequest implements Message {
    private final Ping ping;
}

@Data
class PongReply implements Message {
    private final Pong pong;
}
```

Finally, we come to the other side of this rather uncomplicated conversation,
the client. The `PingClient` is a `Node` which allows the outside world to make
use of our system.

```java
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class PingClient extends Node implements Client {
    private final Address serverAddress;

    private Ping ping;
    private Pong pong;

    /* -------------------------------------------------------------------------
        Construction and Initialization
       -----------------------------------------------------------------------*/
    public PingClient(Address address, Address serverAddress) {
        super(address);
        this.serverAddress = serverAddress;
    }

    @Override
    public synchronized void init() {
        // No initialization necessary
    }

    /* -------------------------------------------------------------------------
        Client Methods
       -----------------------------------------------------------------------*/
    @Override
    public synchronized void sendCommand(Command command) {
        if (!(command instanceof Ping)) {
            throw new IllegalArgumentException();
        }

        Ping p = (Ping) command;

        ping = p;
        pong = null;

        send(new PingRequest(p), serverAddress);
        set(new PingTimer(p), RETRY_MILLIS);
    }

    @Override
    public synchronized boolean hasResult() {
        return pong != null;
    }

    @Override
    public synchronized Result getResult() throws InterruptedException {
        while (pong == null) {
            wait();
        }

        return pong;
    }

    /* -------------------------------------------------------------------------
        Message Handlers
       -----------------------------------------------------------------------*/
    private synchronized void handlePongReply(PongReply m, Address sender) {
        if (Objects.equal(ping.value(), m.pong().value())) {
            pong = m.pong();
            notify();
        }
    }

    /* -------------------------------------------------------------------------
        Timer Handlers
       -----------------------------------------------------------------------*/
    private synchronized void onPingTimer(PingTimer t) {
        if (Objects.equal(ping, t.ping()) && pong == null) {
            send(new PingRequest(ping), serverAddress);
            set(t, RETRY_MILLIS);
        }
    }
}
```

`PingClient` implements the `Client` interface. You should read the
documentation for that interface carefully, as you will soon have to implement
it yourself! When the `PingClient` gets a `Ping` from the calling code, it sends
the `PingRequest` over the network to the server and sets a `PingTimer`. Once
the `PongReply` is received (with the necessary value), the client stores the
result and notifies the calling code which may be waiting.

```java
@Data
final class PingTimer implements Timer {
    static final int RETRY_MILLIS = 10;
    private final Ping ping;
}
```

Once this time elapses after it is set, it will be re-delivered to the
`PingClient`. If the `PingClient` receives this timer and still hasn't received
the necessary `Pong` (e.g., because the `PingRequest` was dropped on the
network), it will send the request again and re-set the timer. In this way, the
`PingClient` continually retries until it gets a response.

## Hello, World!
Now that we have everything in place, let's run our system! We have defined
several basic tests for lab0. The first sets up a single client and a server and
has the client send "Hello, World!" to the server. Let's run it.

```
$ ./run-tests.py --lab 0 --test-num 1

--------------------------------------------------
TEST 1: Single client ping test [RUN] (0pts)

...PASS (0.044s)
==================================================

Tests passed: 1/1
Points: 0/0
Total time: 0.114s

ALL PASS
==================================================
```

That happened a little fast. Let's turn on the built-in logging to watch it in
gory detail.

```
$ ./run-tests.py --lab 0 --test-num 1 --log-level FINEST

--------------------------------------------------
TEST 1: Single client ping test [RUN] (0pts)

[FINEST ] [2018-03-12 22:48:47] [dslabs.framework.Node] MessageSend(client1 -> pingserver, PingRequest(ping=PingApplication.Ping(value=Hello, World!)))
[FINEST ] [2018-03-12 22:48:47] [dslabs.framework.Node] TimerSet(-> client1, PingTimer(ping=PingApplication.Ping(value=Hello, World!)))
[FINEST ] [2018-03-12 22:48:47] [dslabs.framework.Node] MessageReceive(client1 -> pingserver, PingRequest(ping=PingApplication.Ping(value=Hello, World!)))
[FINEST ] [2018-03-12 22:48:47] [dslabs.framework.Node] MessageSend(pingserver -> client1, PongReply(pong=PingApplication.Pong(value=Hello, World!)))
[FINEST ] [2018-03-12 22:48:47] [dslabs.framework.Node] MessageReceive(pingserver -> client1, PongReply(pong=PingApplication.Pong(value=Hello, World!)))
[FINEST ] [2018-03-12 22:48:47] [dslabs.framework.Node] TimerReceive(-> client1, PingTimer(ping=PingApplication.Ping(value=Hello, World!)))
...PASS (0.131s)
==================================================

Tests passed: 1/1
Points: 0/0 (0.00%)
Total time: 0.142s

ALL PASS
==================================================
```

It's alive! We can also use the visual debugger to see our system in action.
Let's do that.

```
$ ./run-tests.py --lab 0 --debug 1 1 "Hello World,Goodbye World"
```

This starts the system with a single server and a single client (in fact, the
first argument to debug, the number of servers, is ignored for this lab and only
used in later labs); the third argument is a comma-separated list defining the
workload given to our client as the values of `Ping`s.

Finally, let's run all of the tests.

```
$ ./run-tests.py --lab 0

--------------------------------------------------
TEST 1: Single client ping test [RUN] (0pts)

...PASS (0.082s)
--------------------------------------------------
TEST 2: Multiple clients can ping simultaneously [RUN] (0pts)

...PASS (0.008s)
--------------------------------------------------
TEST 3: Client can still ping if some messages are dropped [RUN] [UNRELIABLE] (0pts)

...PASS (0.79s)
--------------------------------------------------
TEST 4: Single client repeatedly pings [SEARCH] (0pts)

Checking that the client can finish all pings
Starting breadth-first search...
  Explored: 0, Depth exploring: 0 (0.00s, 0.00K states/s)
  Explored: 20, Depth exploring: 19 (0.04s, 0.49K states/s)
Search finished.

Checking that all of the returned pongs match pings
Starting breadth-first search...
  Explored: 0, Depth exploring: 0 (0.01s, 0.00K states/s)
  Explored: 20, Depth exploring: 19 (0.02s, 1.00K states/s)
Search finished.

...PASS (0.066s)
==================================================

Tests passed: 4/4
Points: 0/0
Total time: 1.207s

ALL PASS
==================================================
```


## When Things Go Wrong
Seeing tests pass is great, but it's not all that exciting. Let's break things
and see what happens.

First, notice that test 3 is marked as "UNRELIABLE." This means that the network
can (and will) randomly drop messages without delivering them. Let's comment-out
a crucial line in `PingClient`. Without re-setting the timer, if one of the
messages gets dropped in the network *again*, the system will be stuck.

```java
private synchronized void onPingTimer(PingTimer t) {
    if (Objects.equal(ping, t.ping()) && pong == null) {
        send(new PingRequest(ping), serverAddress);
        // set(t, RETRY_MILLIS);
    }
}
```

And now let's re-run test 3.

```
$ ./run-tests.py --lab 0 --test-num 3

--------------------------------------------------
TEST 3: Client can still ping if some messages are dropped [RUN] [UNRELIABLE] (0pts)

org.junit.runners.model.TestTimedOutException: test timed out after 5000 milliseconds
  at java.lang.Object.wait(Native Method)
  ...

...FAIL (5.033s)
==================================================

Tests passed: 0/1
Points: 0/0
Total time: 5.103s

FAIL
==================================================
```

What's that you say? Liveness isn't interesting? You want to violate a safety
property? Okay. Let's get rid of the other crucial check in `PingClient`.

```java
private synchronized void handlePongReply(PongReply m, Address sender) {
    // if (Objects.equal(ping.value(), m.pong().value())) {
        pong = m.pong();
        notify();
    // }
}
```

Now, if the client gets an old `Pong` (with an incorrect value), it will
mistakenly accept it and return it to the calling code. This is unlikely to
happen in the first two tests. It might in the third, but this depends on
timing. Instead, we can use the "SEARCH" test to search through all possible
executions of our system for some workload (for more on search tests, see the
[top-level README](../../README.md)).

```
$ ./run-tests.py --lab 0 --test-num 4

--------------------------------------------------
TEST 4: Single client repeatedly pings [SEARCH] (0pts)

Checking that the client can finish all pings
Starting breadth-first search...
  Explored: 0, Depth exploring: 0 (0.00s, 0.00K states/s)
  Explored: 19038, Depth exploring: 11 (1.10s, 17.34K states/s)
Search finished.

Checking that all of the returned pongs match pings
Starting breadth-first search...
  Explored: 0, Depth exploring: 0 (0.00s, 0.00K states/s)
  Explored: 5, Depth exploring: 4 (0.00s, 5.00K states/s)
Search finished.

State(nodes={pingserver=PingServer(super=Node(subNodes={}),
app=PingApplication()),
client1=ClientWorker(client=PingClient(super=Node(subNodes={}),
serverAddress=pingserver, ping=PingApplication.Ping(value=ping-1), pong=null),
results=[])}, network=[Message(client1 -> pingserver,
PingRequest(ping=PingApplication.Ping(value=ping-1)))], timers={pingserver=[],
client1=[Timer(-> client1,
PingTimer(ping=PingApplication.Ping(value=ping-1)))]})

  Message(client1 -> pingserver, PingRequest(ping=PingApplication.Ping(value=ping-1)))

State(nodes={pingserver=PingServer(super=Node(subNodes={}),
app=PingApplication()),
client1=ClientWorker(client=PingClient(super=Node(subNodes={}),
serverAddress=pingserver, ping=PingApplication.Ping(value=ping-1), pong=null),
results=[])}, network=[Message(client1 -> pingserver,
PingRequest(ping=PingApplication.Ping(value=ping-1))), Message(pingserver ->
client1, PongReply(pong=PingApplication.Pong(value=ping-1)))],
timers={pingserver=[], client1=[Timer(-> client1,
PingTimer(ping=PingApplication.Ping(value=ping-1)))]})

  Message(pingserver -> client1, PongReply(pong=PingApplication.Pong(value=ping-1)))

State(nodes={pingserver=PingServer(super=Node(subNodes={}),
app=PingApplication()),
client1=ClientWorker(client=PingClient(super=Node(subNodes={}),
serverAddress=pingserver, ping=PingApplication.Ping(value=ping-2), pong=null),
results=[PingApplication.Pong(value=ping-1)])}, network=[Message(client1 ->
pingserver, PingRequest(ping=PingApplication.Ping(value=ping-2))),
Message(client1 -> pingserver,
PingRequest(ping=PingApplication.Ping(value=ping-1))), Message(pingserver ->
client1, PongReply(pong=PingApplication.Pong(value=ping-1)))],
timers={pingserver=[], client1=[Timer(-> client1,
PingTimer(ping=PingApplication.Ping(value=ping-1))), Timer(-> client1,
PingTimer(ping=PingApplication.Ping(value=ping-2)))]})

  Message(pingserver -> client1, PongReply(pong=PingApplication.Pong(value=ping-1)))

State(nodes={pingserver=PingServer(super=Node(subNodes={}),
app=PingApplication()),
client1=ClientWorker(client=PingClient(super=Node(subNodes={}),
serverAddress=pingserver, ping=PingApplication.Ping(value=ping-3), pong=null),
results=[PingApplication.Pong(value=ping-1),
PingApplication.Pong(value=ping-1)])}, network=[Message(client1 -> pingserver,
PingRequest(ping=PingApplication.Ping(value=ping-2))), Message(client1 ->
pingserver, PingRequest(ping=PingApplication.Ping(value=ping-1))),
Message(pingserver -> client1,
PongReply(pong=PingApplication.Pong(value=ping-1))), Message(client1 ->
pingserver, PingRequest(ping=PingApplication.Ping(value=ping-3)))],
timers={pingserver=[], client1=[Timer(-> client1,
PingTimer(ping=PingApplication.Ping(value=ping-1))), Timer(-> client1,
PingTimer(ping=PingApplication.Ping(value=ping-2))), Timer(-> client1,
PingTimer(ping=PingApplication.Ping(value=ping-3)))]})

dslabs.framework.testing.junit.VizClientStarted: State violates "Clients got expected results"
Error info: client1 got PingApplication.Pong(value=ping-1), expected PingApplication.Pong(value=ping-2)
See above trace.

  at dslabs.framework.testing.junit.BaseJUnitTest.invariantViolated(BaseJUnitTest.java:270)
  ...

...FAIL (1.727s)
==================================================

Tests passed: 0/1
Points: 0/0
Total time: 1.829s

FAIL
==================================================
```

That's a lot of information, but it's important information telling us what went
wrong (the client returned a `Pong` with the wrong value), and gives us a
concrete execution of the system which leads to that problem. Here, the client
sends the first ping to the server. The server responds, and the client sends
out the next ping. Then, the server's response is delivered again (the network
in these labs can duplicate messages unless otherwise stated).

Reading through traces, while useful, can be tedious. Let's visualize it!

```
$ ./run-tests.py --lab 0 --test-num 4 --start-viz
```

Make sure to understand the invariant being violated: the test code "knows" what
to expect as a reply from the ping server (that the reply should match what was
sent). Catching this is simpler than it might be because we assume that the
client only sends one ping at a time, and waits for the previous ping to be
acknowledged before sending a different ping. What should be the client's
behavior when it receives a duplicate (late) message?


### More on Search Tests

Now we will explore how the search tests work in more detail. This will help you
to understand errors that you see so that you can modify your implementation to
work best with the model checker. To do this, we'll see how the search test
checks the correctness of our ping-pong implementation. Before continuing, you
should understand the ping-pong protocol. You should also review the "Search
Tests" section of the [top-level README](../../README.md).

At this point, we've told you that search tests check the correctness of the
implementation by exploring the state graph (a directed graph where the vertices
are states of the system and there is an edge from state `u` to `v` if there is
a message or timer which can be delivered in state `u` to reach state `v`). The
state of the system consists of three parts: (1) the states of the nodes in the
system, (2) the queue of timers pending for each node, and (3) the state of the
network. Let's describe each of these in more detail.

First, the state of the nodes is relatively self-explanatory: it consists of all
`Node` objects involved in the system. Next, the timers pending for each node
can be seen as a queue where the queue ordering respects durations (as described
in the top-level README). What's left is the state of the network, and here
things differ from the view presented in the visual debugger. Specifically, in
the visual debugger, when a message is duplicated, a new copy of the message
appears. As messages can be duplicated any number of times, if the network state
included the number of duplicates present in the system, then the state graph
would be infinite. To avoid this, in search tests, the state of the network is
represented by a set of all messages which have been sent. Messages are not
removed from this set when they are delivered. This incorporates duplications,
delays, and drops: once a message is sent, it can be delivered 0 times (drops),
1 time, or many times (duplicates), and it can be delivered after other messages
and timers have been delivered (delays).

Now we can proceed to the example. The search test for lab 0 has a single server
and a single client which sends 10 different `Ping`s. The test first attempts to
find a sequence of events that leads to the client receiving `Pong`s for all of
its `Ping`s. It then explores the entire state graph and checks that the `Pong`s
received by the client match the `Ping`s sent. To simplify the example, we'll
consider a client which only sends two `Ping`s with values "ping-1" and
"ping-2". This is the search that would occur if you replace the first line of
the search test in [the test file](./tst/dslabs/pingpong/PingTest.java) with

```java
initSearchState.addClientWorker(client(1), repeatedPings(2));
```

Let's explore what happens when we run the first BFS which looks for a state
where the client has received results for all of its sent commands. To begin, we
show the states and edges of this state graph, and then we explain the graph in
more detail.

![State Graph](./img/state-graph.png)

The vertex labels in the state graph describe the state of the nodes, timer
queues, and network.
* Recall that the state of a node (server/client) is simply the fields of the
  server/client object. The `PingServer` has an `app` field, while the
  `PingClient` has a `serverAddress`, `ping`, and `pong` field. The first two
  lines describe the state of the two nodes in the system (we omit the fields in
  the `Node` superclass as those details are not important to us).
* The next two lines describe the client and server timer queues. These are
  given as a list where the list ordering must respect durations.
* The last line(s) store the state of the network, all messages ever sent. These
  describe both the message contents and the sender/receiver of the message.

The very first state in the graph is the system's initial state -- that is, the
state after all nodes have been initialized and the client has been told to send
the first command (via `sendCommand(Ping("ping-1"))`). At this point, the client
has sent a `PingRequest` to the server and has set a `PingTimer`, but it has not
yet received any results. After construction and initialization, the server's
state is rather boring: it has a `PingApplication` with no fields, and it
doesn't set any timers in `init()`.

From this starting state there are two possible events: we can fire the timer,
or deliver the message.
* If we fire the `PingTimer`, then the client will see that the timer matches
  the last sent `Ping` and there is no `Pong` received for the `Ping`, so it
  will resend the `PingRequest` to the server and reset the timer. Thus, the
  client's timer queue again has `[PingTimer(Ping("ping-1"))]`. Moreover, the
  network set already has this `PingRequest` from client to server, so the
  network set doesn't change either. Therefore, we return to the initial state.
* If we deliver the `PingRequest`, the server executes the request and replies
  with a `PongReply`. The server's state and timer queue remain the same, but
  now the network has the `PongReply` that was sent from server to client. The
  original `PingRequest` remains in the network and can be delivered again.

This explains all of the edges from the starting state, and it explains how we
reach the second state in the graph. The client is still waiting for results, so
the BFS continues.

From this state, we can fire the timer or deliver either of the messages.
* **Test your understanding:** you should now be able to explain why firing the
  `PingTimer` or delivering the `PingRequest` returns us to the same state.
* When delivering the `PongReply`, many things happen.
  * The client determines that the `PongReply` is a reply for the current
    command, so it updates its `pong` field to match.
  * The framework determines that the client received a result (via
    `hasResult()`) and saves the result (via `getResult()`) in the `results`
    list.
  * Since the client received a result, the framework tells the client to send
    the next command (`sendCommand(Ping("ping-2"))`).
    * The client updates its `ping` field to this new command, and sets its
      `pong` field to null.
    * The client sends a `PingRequest` for this command to the server.
    * The client sets a `PingTimer` for this command. Since the timer ordering
      must respect durations, `PingTimer(Ping("ping-1"))` must fire before
      `PingTimer(Ping("ping-2"))`.

So, we've explained the edges leaving the second state, and we've explained why
the third state is different from the second. In the third state, the client is
still waiting for a result, and all received results match the expected results.
Therefore, the BFS continues.

We can fire the first timer in queue or deliver any of the messages.
* If we fire the first `PingTimer` in queue, then the client will recognize that
  the timer is out-of-date and therefore drop it. No other actions will be
  taken. So, we get a new state which is almost the same as the original, but
  the new client queue has only the second timer.
* If we deliver `ping-1`'s `PingRequest` or `PongReply`, then we return to the
  same state. Once again, you should be able to explain why this is the case.
* If we deliver the `PingRequest` for `ping-2`, then the server executes the
  `Ping` and replies. Thus, node states and timer queues remain the same, but
  the network now has a `PongReply` for `ping-2`.

The BFS continues on in this fashion until it either exhausts all edges, finds
an invariant violation (a state where the `Pong` returned doesn't match the
`Ping` sent), or finds a state matching our goal.

In this case, the BFS finishes when it finds the state in the bottom-right,
where the client is done and has received `Pong`s for all `Ping`s in its
workload. All received results match the expected results. So, the BFS finishes
and we have found a state matching the goal. At this point, we know that our
system can reach a result. We also are more confident in the safety of our
system, since in the states we examined, we checked that the received results
matched the expected results.

Here are some questions you can use to test your understanding of search tests:
* Consider the modification outlined earlier in this section: in
  `handlePongReply`, the client doesn't check that the pong value matches the
  ping value. Carry out the BFS by hand as we did above, and give the state at
  which an invariant violation occurs.
* From the correct solution (the initially provided one), comment out the line
  `send(new PongReply(p), sender)` in the server's `handlePingRequest`. Now, it
  should be impossible for the client to receive results.
  * First, the theory: write out the state graph for this modified solution. How
    can the search test tell that the client's workload cannot be finished?
  * Now, the practice: run the test and see the error that the search test gives
    you.
    * Optionally, open the visual debugger and try to deliver messages to get a
      result to the client. For this simple liveness issue, it may seem
      unnecessary to use the visual debugger, but for the more complicated
      protocols you will work with, the debugger can help you to see where your
      implementation differs from your protocol design (or a situation you
      missed when designing the protocol).
* The test in lab 0 has another search:
  ```java
  searchSettings.clearGoals().addPrune(CLIENTS_DONE)
  bfs(initSearchState);
  assertSpaceExhausted();
  ```
  
* This will run the BFS as described earlier, checking that the results match
  the expected results, but this test does not have a goal. When the search
  reaches a state where the client is done, the test will not end; instead, it
  will continue examining any remaining states where the client is not done.
  That is to say, the test "prunes" any parts of the search space which follow a
  state where the clients are finished. The test asserts that it can exhaust the
  search space; in other words, that in the time allotted, it can examine the
  entire graph (except the states which were pruned).

  Carry out this pruned BFS by hand (still assuming that the client sends only 2
  commands rather than 10), and confirm that you can exhaust the search space.
