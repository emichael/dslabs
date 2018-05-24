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
with a standard command-driven interface, the rest of the code you write will be
application-agnostic, and so can be used for any underlying application (for
example, a network file system).

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
fire iff you explicitly set a timeout.

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
messages and set timeouts, respectively. The server's address is available via
`serverAddress` in the client.

Our solution to part 2 took approximately 40 lines of code.

You should pass the part 2 tests; execute them with `run-tests.py --lab 1 --part
2`.


## Part 3: Once and Only Once
Finally, you need to modify your solution to Part 2 to guarantee exactly-once
delivery. The simplest way to do this is to modify the client and server code
directly. The test code for Lab 1 only exercises the system through the client
interface, and so this will allow you to pass the tests.

However, we recommend doing so via adding a shim layer to an Application that
ensures that each unique command is executed at most once. (Along with your at
least once code from Part 2, that provides exactly once.) The reason is that we
will want at most once semantics in later labs, and this will keep you from
having to reimplement the functionality for Lab2 and Lab 3 and Lab 4.

We've started you in the right direction by providing the `atmostonce` package.
You will need to implement the classes in this package and possibly modify the
other parts of your solution -- the messages, timeouts, client, and server -- to
work with these changes.

The `AMOApplication` is an `Application` that takes any `Application` and turns
it into an `Application` capable of guaranteeing at-most-once execution of
`AMOCommand`s. As the provided code indicates, it should reject any `Command`s
that are not `AMOCommand`s (though, `executeReadOnly` allows callers to
optionally execute non-AMO reads, which you might find useful in later labs).

Some steps that may be needed: wrap the provided `Application` in the
constructor in an `AMOApplication` (and perhaps changing the corresponding
field's declared type). You will also need to deal with what happens when the
server receives "old" `Request`s. The modifications to the client should be
similarly simple. Your modifications to `Request`, `Reply`, and `ClientTimeout`
should only take a couple lines each; the metadata you added in part 2 can
probably be removed (it should have been subsumed by metadata kept in
`AMOCommand` and `AMOResult`).

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
