/*
 * Copyright (c) 2018 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

/**
 * <p>Nodes are the basic unit of computation. They can send and receive {@link
 * Message}s, set and handle {@link Timer}s, and modify private data. These
 * handlers (as well {@link Node#init()}) are invoked <i>sequentially</i>, and
 * they should deterministically run to completion <b>without blocking,
 * sleeping, or starting other threads</b>.
 *
 * <p>Nodes need not handle concurrent access, except for {@link Client}s.
 * Nodes should not use any other means to communicate (e.g., communication
 * through static variables is forbidden). Subclasses of Node define {@link
 * Message} handlers by creating methods with the correct name and method
 * signature. For instance, to define a message handler for {@code Foo extends
 * Message}, a Node would define the method {@code handleFoo(Foo message,
 * Address sender)}. Similarly, to define a handler for {@code Bar extends
 * Timer} a Node would define the method {@code onBar(Bar timer)}.
 *
 * <p>After creation (but before any {@link Message} or {@link Timer}
 * handlers are invoked), the {@link Node#init()} method will be invoked. Nodes
 * should <b>not</b> send any messages or set any timers in their constructor.
 * Instead, they should send any necessary messages during initialization.
 *
 * <p>Nodes can add sub-Nodes, which allow code re-use. When a Node is
 * registered as a sub-Node, it can send messages and set timers as normal.
 * However, messages can also be passed <i>reliably</i> and immediately between
 * the sub-Node and its parent using {@link #handleMessage(Message, Address)}.
 * The parent node registering the sub-Node is responsible for creating the
 * sub-Node with a sub-Address of its own address ({@link
 * Address#subAddress(Address, String)}), registering the sub-Node {@link
 * #addSubNode(Node)}, and then initializing the sub-Node by calling its {@link
 * #init()} method <i>after the parent node has been initialized</i>. Nodes do
 * not need to keep references to their sub-Nodes or parent nodes. Instead, they
 * should keep only the address of the other and communicate through
 * message-passing. The following is an example of how to create a sub-Node:
 *
 * <pre><code>
 * public void init() {
 *     Address subNodeAddress = Address.subAddress(address(), "foo");
 *     SubNode subNode = new SubNode(subNodeAddress, address());
 *     addSubNode(subNode);
 *     subNode.init();
 * }
 * </code></pre>
 *
 * <p>The parent node keeps a reference to subNodeAddress, and the sub-Node
 * stores a reference to the parent's address in its constructor, allowing them
 * to communicate.
 *
 * <p>Subclasses of Node must properly implement {@link Node#equals(Object)},
 * {@link Node#hashCode()}, and {@link Node#toString()}. Subclasses of Node
 * <b>must</b> call this class's implementations of those methods (since
 * sub-Nodes and their state are stored in this class). All data structures held
 * in Nodes must properly implement {@link Node#equals(Object)}, {@link
 * Node#hashCode()}, and {@link Node#toString()}. Furthermore, these data
 * structures must also implement {@link Serializable}.
 *
 * <p>To facilitate the copy-on-write style cloning in search tests, subclasses
 * of Node should not use locking or synchronized data structures (e.g.,
 * Hashtable), the one exception being the synchronization required in {@link
 * Client} methods. Doing so has the potential to cause deadlock in those
 * tests.
 */
@Log
@EqualsAndHashCode(of = {"subNodes"})
@ToString(of = {"address", "subNodes"})
public abstract class Node implements Serializable {
    private static final Map<Class<? extends Node>, Map<String, Optional<Method>>>
            methods = new ConcurrentHashMap<>();

    /**
     * This Node's address.
     */
    @VizIgnore @NonNull private final Address address;

    transient private Consumer<Triple<Address, Address, Message>> messageAdder;
    transient private Consumer<Triple<Address, Address[], Message>>
            batchMessageAdder;
    transient private Consumer<Triple<Address, Timer, Pair<Integer, Integer>>>
            timerAdder;
    transient private Consumer<Throwable> throwableCatcher;
    transient private Boolean logExceptions = true;

    /**
     * The Node's parent (or null if this Node is the root Node in the
     * hierarchy).
     */
    @VizIgnore private Node parentNode;

    /**
     * This Node's sub-Nodes, indexed by their ID. Sub-Nodes must have a
     * {@link SubAddress} composed of this Node's address and their ID.
     */
    private final Map<String, Node> subNodes = new HashMap<>();

    /**
     * Constructor for a Node which all subclasses must call.
     *
     * @param address
     *         the address of the Node
     */
    protected Node(@NonNull Address address) {
        this.address = address;
    }

    /**
     * Takes any initialization steps necessary (potentially sending {@link
     * Message}s and setting {@link Timer}s).
     */
    public abstract void init();

    /**
     * Adds a sub-Node to this Node's hierarchy. The address of the sub-Node
     * must be a sub-Address of this Node's Address. Does not automatically
     * initialize the sub-Node.
     *
     * @param subNode
     *         the sub-Node to add
     */
    protected final void addSubNode(@NonNull Node subNode) {
        Address sa = subNode.address;
        if (!(sa instanceof SubAddress &&
                Objects.equals(address, ((SubAddress) sa).parentAddress()))) {
            throw new IllegalArgumentException(
                    "Attempting to add subNode with address that isn't a subAddress of this node.");
        }

        if (subNode.messageAdder != null || subNode.batchMessageAdder != null ||
                subNode.timerAdder != null) {
            throw new IllegalArgumentException(
                    "Cannot configure node; already configured as stand-alone.");
        }

        SubAddress subAddress = (SubAddress) subNode.address;

        if (subNodes.containsKey(subAddress.id())) {
            throw new IllegalArgumentException(
                    "Node already has sub-Node with id: " + subAddress.id());
        }

        subNode.parentNode = this;
        subNodes.put(subAddress.id(), subNode);
    }

    /**
     * The address of this Node.
     *
     * @return the address
     */
    public final Address address() {
        return address;
    }

    /**
     * Send a message to a Node with the given {@link Address}. The message will
     * be cloned or serialized immediately as it is sent; there is no need to
     * deep copy data structures when creating messages.
     *
     * @param message
     *         the message to send
     * @param to
     *         the destination address
     */
    protected void send(Message message, Address to) {
        send(message, address, to);
    }

    /**
     * Sends a message to all Nodes in the array.
     *
     * @param message
     *         the message to send
     * @param to
     *         the destination addresses
     */
    protected void broadcast(Message message, Address[] to) {
        broadcast(message, address, to);
    }

    /**
     * Sends a message to all Nodes in the collection.
     *
     * @param message
     *         the message to send
     * @param to
     *         the destination addresses
     */

    protected void broadcast(Message message, Collection<Address> to) {
        broadcast(message, to.toArray(new Address[0]));
    }

    /**
     * Sets a {@link Timer} to be tracked by the environment. The Timer will be
     * re-delivered to the setting {@link Node} after timerLengthMillis
     * milliseconds. Timers may be cloned by the testing infrastructure before
     * being re-delivered.
     *
     * @param timer
     *         the timer to set
     * @param timerLengthMillis
     *         the timer duration
     */
    protected void set(Timer timer, int timerLengthMillis) {
        set(timer, timerLengthMillis, timerLengthMillis, address);
    }

    /**
     * Sets a {@link Timer} to be tracked by the environment. The Timer will be
     * re-delivered to the setting {@link Node} between minTimerLengthMillis and
     * maxTimerLengthMillis, inclusive, chosen uniformly at random. Timers may
     * be cloned by the testing infrastructure before being re-delivered.
     *
     * @param timer
     *         the timer to set
     * @param minTimerLengthMillis
     *         the minimum timer duration
     * @param maxTimerLengthMillis
     *         the maximum timer duration
     */
    protected void set(Timer timer, int minTimerLengthMillis,
                       int maxTimerLengthMillis) {
        if (minTimerLengthMillis > maxTimerLengthMillis) {
            throw new IllegalArgumentException(
                    "Minimum timer length greater than maximum timer length");
        }

        // TODO: test this in TimerEnvelope too? What about Message Envelope?

        if (minTimerLengthMillis < 1) {
            throw new IllegalArgumentException("Minimum timer length < 1ms");
        }

        set(timer, minTimerLengthMillis, maxTimerLengthMillis, address);
    }

    private void send(Message message, Address from, Address to) {
        if (message == null) {
            LOG.severe(String.format(
                    "Attempting to send null message from %s to %s, not sending",
                    from, to));
            return;
        }

        if (to == null) {
            LOG.severe(String.format(
                    "Attempting to send %s from %s to null address, not sending",
                    message, from));
            return;
        }

        LOG.finest(() -> String
                .format("MessageSend(%s -> %s, %s)", from, to, message));

        if (messageAdder != null) {
            messageAdder.accept(new ImmutableTriple<>(from, to, message));
        } else if (batchMessageAdder != null) {
            batchMessageAdder
                    .accept(new ImmutableTriple<>(from, new Address[]{to},
                            message));
        } else if (parentNode != null) {
            parentNode.send(message, from, to);
        } else {
            LOG.severe(String.format(
                    "Attempting to send %s from %s to %s before node configured, not sending",
                    message, from, to));
        }
    }

    private void broadcast(Message message, Address from, Address[] to) {
        if (message == null) {
            LOG.severe(String.format(
                    "Attempting to send null message from %s to %s, not sending",
                    from, Arrays.toString(to)));
            return;
        }

        // TODO: use send instead of broadcast when to.length == 1?
        // TODO: check for to.length == 0

        for (Address a : to) {
            if (a == null) {
                LOG.severe(String.format(
                        "Attempting to send %s from %s to set of nodes including null, not sending message to any recipient",
                        message, from));
                return;
            }
        }

        LOG.finest(() -> String
                .format("MessageSend(%s -> %s, %s)", from, Arrays.toString(to),
                        message));

        if (batchMessageAdder != null) {
            batchMessageAdder.accept(new ImmutableTriple<>(from, to, message));
        } else if (messageAdder != null) {
            for (Address a : to) {
                messageAdder.accept(new ImmutableTriple<>(from, a, message));
            }
        } else if (parentNode != null) {
            parentNode.broadcast(message, from, to);
        } else {
            LOG.severe(String.format(
                    "Attempting to send %s from %s to %s before node configured, not sending",
                    message, from, Arrays.toString(to)));
        }
    }

    private void set(Timer timer, int minTimerLengthMillis,
                     int maxTimerLengthMillis, Address from) {
        if (timer == null) {
            LOG.severe(String.format(
                    "Attempting to set null timer for %s, not setting", from));
            return;
        }

        LOG.finest(() -> String.format("TimerSet(-> %s, %s)", from, timer));

        if (timerAdder != null) {
            timerAdder.accept(new ImmutableTriple<>(from, timer,
                    new ImmutablePair<>(minTimerLengthMillis,
                            maxTimerLengthMillis)));
        } else if (parentNode != null) {
            parentNode.set(timer, minTimerLengthMillis, maxTimerLengthMillis,
                    from);
        } else {
            LOG.severe(String.format(
                    "Attempting to set %s from %s before node configured, not setting",
                    timer, from));
        }
    }

    private Object handleMessageInternal(Message message, Address sender,
                                         Address destination, boolean handleExceptions) {
        if (message == null) {
            LOG.severe(String.format(
                    "Attempting to deliver null message from %s to %s", sender,
                    destination));
            return null;
        }

        if (!Objects.equals(address.rootAddress(), destination.rootAddress())) {
            LOG.severe(String.format(
                    "Attempting to deliver message with destination %s to node %s, not delivering",
                    destination, address));
            return null;
        }

        LOG.finer(() -> String
                .format("MessageReceive(%s -> %s, %s)", sender, destination, message));

        String handlerName = "handle" + message.getClass().getSimpleName();
        return callMethod(destination, handlerName, handleExceptions, message, sender);
    }

    /**
     * <p><b>Do not use.</b> Only used by testing framework.
     *
     * <p>Uses reflection to find the appropriate message handler; calls that
     * handler with the given arguments.
     *
     * @param message
     *         the message to deliver
     * @param sender
     *         the sender of the message
     * @param destination
     *         the Node to deliver to
     * @hidden
     */
    public void handleMessage(Message message, Address sender,
                              Address destination) {
        handleMessageInternal(message, sender, destination, true);
    }

    /**
     * <p>Can be used to send messages between two nodes within the same root
     * node (e.g., between parent Node and sub-Node). The message is handled
     * <i>immediately</i>. If the handler is successfully executed and returns
     * a value, that value is returned. Otherwise, this method returns null.
     *
     * <p>The message and the return value are <b>not cloned or modified in any
     * way</b>; note that this behavior differs from
     * {@link #send(Message, Address)}, which clones or serializes messages
     * immediately. If the caller wants to mirror the behavior of
     * {@link #send(Message, Address)}, the recommended method is to implement
     * {@link Cloneable} and {@link Object#clone()}, call {@link Object#clone()}
     * on the message, and pass the cloned result to this method. Alternatively,
     * {@link org.apache.commons.lang3.SerializationUtils#clone(Serializable)}
     * can be used to clone objects without implementing {@link Object#clone()},
     * but it is <i>much slower</i>.
     *
     * @param message
     *         the message to deliver
     * @param destination
     *         the Node to deliver to
     * @return the value returned by the handler or null
     */
    protected final Object handleMessage(Message message, Address destination) {
        return handleMessageInternal(message, address, destination, false);
    }

    /**
     * <p>Can be used to handle a message sent by a Node to itself locally
     * (rather than sending the message over the network). The message is
     * handled <i>immediately</i>. If the handler is successfully executed and
     * returns a value, that value is returned. Otherwise, this method returns
     * null.
     *
     * <p>The message and the return value are <b>not cloned or modified in any
     * way</b>; note that this behavior differs from
     * {@link #send(Message, Address)}, which clones or serializes messages
     * immediately. If the caller wants to mirror the behavior of
     * {@link #send(Message, Address)}, the recommended method is to implement
     * {@link Cloneable} and {@link Object#clone()}, call {@link Object#clone()}
     * on the message, and pass the cloned result to this method. Alternatively,
     * {@link org.apache.commons.lang3.SerializationUtils#clone(Serializable)}
     * can be used to clone objects without implementing {@link Object#clone()},
     * but it is <i>much slower</i>.
     *
     * @param message
     *         the message to deliver
     * @return the value returned by the handler or null
     */
    protected final Object handleMessage(Message message) {
        return handleMessageInternal(message, address, address, false);
    }

    private void onTimerInternal(Timer timer, Address destination, boolean handleExceptions) {
        if (timer == null) {
            LOG.severe(String.format("Attempting to deliver null timer to %s",
                    address));
            return;
        }

        if (!Objects.equals(address.rootAddress(), destination.rootAddress())) {
            LOG.severe(String.format(
                    "Attempting to deliver message with destination %s to node %s, not delivering",
                    destination, address));
            return;
        }

        LOG.finer(() -> String
                .format("TimerReceive(-> %s, %s)", destination, timer));

        String handlerName = "on" + timer.getClass().getSimpleName();
        callMethod(destination, handlerName, handleExceptions, timer);
    }

    /**
     * <p><b>Do not use.</b> Only used by testing framework.</p>
     *
     * <p>Uses reflection to find the appropriate timer handler; calls that
     * handler with the given argument.</p>
     *
     * @param timer
     *         the timer to deliver
     * @param destination
     *         the Node to deliver to
     * @hidden
     */
    public void onTimer(Timer timer, Address destination) {
        onTimerInternal(timer, destination, true);
    }

    /**
     * <p>Can be used to invoke a timer handler on a Node, rather than
     * setting the timer and waiting for it to expire. The timer handler is
     * handled <i>immediately</i>.
     *
     * <p>The timer is not cloned or modified in any way.
     *
     * @param timer
     *         the timer to deliver
     */
    protected final void onTimer(Timer timer) {
        onTimerInternal(timer, address, false);
    }

    @SneakyThrows
    private Object callMethod(Address destination, String methodName,
                              boolean handleExceptions, Object... args) {
        // Grab a reference to the root node in this hierarchy
        Node n = this;
        while (n.parentNode != null) {
            n = n.parentNode;
        }

        // Get the full sub-Node path
        List<String> path = new LinkedList<>();
        while (destination instanceof SubAddress) {
            path.add(((SubAddress) destination).id());
            destination = ((SubAddress) destination).parentAddress();
        }
        Collections.reverse(path);

        // Traverse from the root node to the sub-Node
        for (String id : path) {
            if (!n.subNodes.containsKey(id)) {
                LOG.severe(String.format("Could not find subNode %s of %s", id,
                        n.address));
                return null;
            }
            n = n.subNodes.get(id);
        }

        final Class<? extends Node> c = n.getClass();
        final Map<String, Optional<Method>> methodMap =
                methods.computeIfAbsent(c, __ -> new ConcurrentHashMap<>());
        final Optional<Method> method =
                methodMap.computeIfAbsent(methodName, __ -> {
                    Class<?> currentClass = c;
                    // TODO: fix this hack, find a better way to look for methods?
                    while (!currentClass.equals(Object.class)) {
                        for (Method m : currentClass.getDeclaredMethods()) {
                            if (m.getName().equals(methodName)) {
                                m.setAccessible(true);
                                return Optional.of(m);
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }
                    return Optional.empty();
                });

        if (method.isEmpty()) {
            LOG.severe(String.format(
                    "Could not find method %s from %s with args %s", methodName,
                    c.getSimpleName(), Arrays.toString(args)));
            return null;
        }

        try {
            return method.get().invoke(n, args);
        } catch (Exception e) {
            Throwable t = e;

            if (e instanceof InvocationTargetException) {
                t = ((InvocationTargetException) e).getTargetException();
            }

            if (!handleExceptions) {
                throw t;
            }

            if (logExceptions) {
                LOG.log(Level.SEVERE, String.format(
                        "Error invoking method %s from %s with args %s",
                        methodName, n.getClass().getSimpleName(),
                        Arrays.toString(args)), t);
            }

            if (throwableCatcher != null) {
                throwableCatcher.accept(t);
            }
        }

        return null;
    }

    /**
     * <p><b>Do not use.</b> Only used by testing framework.
     *
     * <p>Configures the node to allow it to send messages and set timers.
     *
     * <p>At least one of {@code messageAdder}/{@code batchMessageAdder} must
     * be non-null.
     *
     * @param messageAdder
     *         a function which consumes messages sent by the node, or
     *         {@code null} to have the node send all messages to the
     *         {@code batchMessageAdder}
     * @param batchMessageAdder
     *         a function which consumes messages sent by the node to multiple
     *         recipients, or {@code null} to have the node send all messages to
     *         the {@code messageAdder}
     * @param timerAdder
     *         a function which consumes timers set by the node
     * @param throwableCatcher
     *         a function which consumes exceptions thrown by the node during
     *         message and timer handling, or {@code null} to have the node drop
     *         exceptions
     * @param logExceptions
     *         whether to log exceptions thrown by the node during message and
     *         timer handling, in addition to sending them to the
     *         {@code throwableCatcher}
     * @hidden
     */
    public void config(Consumer<Triple<Address, Address, Message>> messageAdder,
                       Consumer<Triple<Address, Address[], Message>> batchMessageAdder,
                       @NonNull Consumer<Triple<Address, Timer, Pair<Integer, Integer>>> timerAdder,
                       Consumer<Throwable> throwableCatcher,
                       boolean logExceptions) {
        if (parentNode != null) {
            LOG.severe("Cannot configure Node already configured as sub-Node.");
        }

        if (messageAdder == null && batchMessageAdder == null) {
            LOG.severe(
                    "Cannot configure Node without messageAdder or batchMessageAdder.");
        }

        this.messageAdder = messageAdder;
        this.batchMessageAdder = batchMessageAdder;
        this.timerAdder = timerAdder;
        this.throwableCatcher = throwableCatcher;
        this.logExceptions = logExceptions;
    }
}
