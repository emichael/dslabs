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
