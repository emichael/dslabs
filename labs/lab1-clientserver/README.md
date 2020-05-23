# Lab 1: Client-Server
In this lab you will implement an unreplicated server capable of processing
commands from multiple clients. By the end of the lab, you will have implemented
an "exactly-once" RPC abstraction for sending commands from clients to the
server on top of a message-passing layer.

Before you begin, be sure to check out [Lab 0](../lab0-pingpong/README.md) and
carefully read the [top-level README](../../README.md). Make sure you understand
the framework and the way the `Node` interface works before beginning. You may
ignore the sub-Node feature for now, though; you will not need it until later
labs. It may also helpful to read the instructions for all three parts before
starting on part 1.

The provided tests will exercise various aspects of your implementations, but
they are not complete! Your primary goal should be to *understand* the
distributed systems the labs describe and then correctly implement them. When
you encounter bugs, while you have a variety of debugging tools at your
disposal, the most important part of the debugging process is thinking about why
your implementation is doing what it's doing and how that differs from what
should be happening. Making changes to your code without understanding what is
happening is unlikely to be useful – neither to your implementation nor your
understanding of distributed systems.


## Part 1: A Key-Value Store
First, you should implement the application interface in `KVStore.java`. This is
the application that we'll use throughout the labs. By isolating it into a class
with a standard command-driven interface, the majority of the code you write
will be application-agnostic, and so can be used for any underlying application
(for example, a network file system).

Your key-value store should support three commands: `Get`, `Put`, and `Append`.
Get returns the value of the key in a `GetResult`, if the key exists; otherwise
it returns a `KeyNotFound`. `Put` associates a key with a value and returns
`PutOk`. `Append` appends a value to the current value of a key (or simply
associates the key with a value if it does not yet exist). `Append` returns the
*new* value of the key in an `AppendResult`.

Our solution to part 1 took 11 lines of code.

You should pass the part 1 tests before moving on to part 2; execute them with
`run-tests.py --lab 1 --part 1`.

**Hint:** your `KVStore` should essentially be a wrapper around a common data
structure from the `java.util` package.


## Part 2: At Least Once
Next, you'll implement a very basic client and server that handles network
failures (`SimpleClient.java` and `SimpleServer.java`) by retrying messages that
may have been lost. For this part of the lab, you need only implement
at-least-once semantics; at-most-once execution comes in part 3.

In particular, you should think about what happens in the various cases: What if
the request message is dropped on the way to the server? What if the reply is
dropped? What if the reply is delayed? What if the the request is delayed so
that the retry arrives at the server and then the original request arrives; and
so forth.

You can (and should) make the simplifying assumption that a client will have
only one outstanding request at a time. (In practice, many distributed systems
allow the client to issue multiple simultaneous requests; the bookkeeping for
that is a bit more involved.) Also, recall that the timer interrupt handler will
fire iff you explicitly set a timer.

The client interface includes both a polling and a blocking interface.
`hasResult` should return whether the client has a result for the most recent
command. It always returns immediately. `getResult` should return the actual
result for the most recent command. If the client does not have a result for the
most recent command, `getResult` should block until there is a result.

For the solution to this part, your implementation is likely to need to keep
metadata in the client about the state of pending requests and you will need to
define message fields to help keep track of which messages need to be retried.

**Hint:** for part 2, `Request` and `Reply` should probably be defined as
follows.

```java
@Data
class Request implements Message {
	private final Command command;
	private final int sequenceNum;
}

@Data
class Reply implements Message {
	private final Result result;
	private final int sequenceNum;
}
```

Make sure you read the documentation for the `Client` class! It contains
important information that you'll need when implementing its interface. You
should use standard Java synchronization mechanisms to block and wait for
results in `getResult`. Our solution used one call to `this.wait()` and
`this.notify()` each in `SimpleClient`.

Your client and server code should use `this.send` and `this.set` to send
messages and set timers, respectively. The server's address is available via
`serverAddress` in the client.

Our solution to part 2 took approximately 40 lines of code.

You should pass the part 2 tests; execute them with `run-tests.py --lab 1 --part
2`. If your implementation finishes all tests but sometimes returns incorrect
results (because appends are executed more than once), you should continue on to
part 3. Part 2 of this lab is just intended as a stepping stone to part 3; the
bug should become apparent later.


### A Note About Integers
The code above uses `int`s as sequence numbers. You might be wondering about
integer overflow. This is an important concern! However, for simplicity in these
labs, you can assume that the number of commands sent in each test is much
smaller than `2^31 - 1`. In practice, you could use a larger fixed size value
(128 bits should be sufficient) or an arbitrary-precision value (e.g.,
`BigInteger` in Java).


## Part 3: Once and Only Once
Finally, you need to modify your solution to Part 2 to guarantee exactly-once
delivery. The simplest way to do this is to modify the client and server code
directly. The test code for Lab 1 only exercises the system through the client
interface, and so this will allow you to pass the tests.

However, we recommend doing so via adding a shim layer to an Application that
ensures that each unique command is executed at most once. (Along with your at
least once code from Part 2, that provides exactly once.) The reason is that we
will want at most once semantics in later labs, and this will keep you from
having to reimplement the functionality for Labs 2, 3, and 4.

We've started you in the right direction by providing the `atmostonce` package.
You will need to implement the classes in this package and possibly modify the
other parts of your solution -- the messages, timers, client, and server -- to
work with these changes.

The `AMOApplication` is an `Application` that takes any `Application` and turns
it into an `Application` capable of guaranteeing at-most-once execution of
`AMOCommand`s. As the provided code indicates, it should reject any `Command`s
that are not `AMOCommand`s (though, `executeReadOnly` allows callers to
optionally execute non-AMO reads, which you will find useful in later labs).

Some steps that may be needed: wrap the provided `Application` in the
constructor in an `AMOApplication` (and perhaps changing the corresponding
field's declared type). You will also need to deal with what happens when the
server receives "old" `Request`s. The modifications to the client should be
similarly simple. Your modifications to `Request`, `Reply`, and `ClientTimer`
should only take a couple lines each; the metadata you added in part 2 can
probably be removed (it should have been subsumed by metadata kept in
`AMOCommand` and `AMOResult`).


### Garbage Collection
The test suite ensures that old commands are garbage collected properly from all
of your nodes' data structures. In particular, you will need to ensure that your
`AMOApplication` does not store unnecessary information. Remember, clients in
this framework only have one outstanding `Request` at a time. The test
infrastructure will never call `sendCommand` twice in a row without getting the
result for the first command.

One potential pitfall is that non-static inner classes implicitly contain a
reference to the enclosing object. If they are serialized, this reference will
be serialized as well. This can cause your implementation to fail the garbage
collection tests. You should prefer to make all inner classes you create static
unless you have a good reason not to and you understand the implications.


### Designing with Timers
The test suite also makes sure that your clients and server continue to perform
well when the system has been running for a while. One important thing to note
is that the event loop delivering messages and timers to your nodes prioritizes
timers that are due. If a node sets too many timers and every time a timer
fires, it resets that timer, it might eventually get into a state where its
timer queue is so long that the node only processes those timers and never does
anything else!

The workloads your clients are running in this long test continuously send new
commands as soon as they receive the result for the previous one, so your
`SimpleClient` might be susceptible to this problem with `ClientTimer`s.

There are two important protocol design patterns you should know:
1. **The Resend/Discard Pattern:** Nodes set timers when they need responses to
   the messages they send. If that timer fires before the required response is
   received, the node resends the message and resets the timer. Importantly, *if
   the timer fires after the required response is received, the node should drop
   the timer (i.e., not reset it).* Otherwise, your system could run into the
   problem described above. This pattern usually requires the timer itself
   having enough state to describe the response needed.
2. **The Tick Pattern:** Nodes set a single timer on `init`. Then, every time
   that timer fires, the node takes some action (e.g., resends messages awaiting
   responses) and also resets the timer. Thus, there is always exactly one timer
   of that type in the node's queue, which fires every `timerLengthMillis`
   (every "tick").

There are trade-offs to make with both of these patterns, and you should think
about their performance implications. The tick pattern is often simpler and more
conducive to exploratory model-checking, but it sometimes results in sending of
unnecessary messages.

One last note on timers, which is important for both of the above patterns: when
a node resets a timer inside a timer handler, it is often best practice to wait
until the very end of the timer handler to reset it, so that the node doesn't
get into an infinite loop by taking too long in the rest of the method.


### Lab 1 Search Tests
Some of the state space search tests in this lab assert something stronger than
in later labs. These tests assert that they can exhaust the state space
entirely, rather than simply not finding an invariant-violating state in a
certain amount of time. This means that for a given configuration (i.e., how
many clients there are and which commands those clients will send), your state
space should be finite. In particular, you should not increment sequence numbers
unnecessarily. This comes with two benefits: (1) it's good practice to number
the commands sequentially rather than simply monotonically, and (2) the search
test will guarantee that for a given configuration *all possible executions*
preserve linearizability! Because the tests are configured with fairly
representative workloads, this is strong evidence that your system will preserve
linearizability for all possible executions of all possible configurations.


---


**Hint:** for part 3, `Request` and `Reply` should probably be defined as
follows.

```java
@Data
class Request implements Message {
	private final AMOCommand command;
}

@Data
class Reply implements Message {
	private final AMOResult result;
}
```

Our solution to part 3 took approximately 15 lines of code.

You should now pass the part 3 tests; execute them with `run-tests.py --lab 1
--part 3`.
