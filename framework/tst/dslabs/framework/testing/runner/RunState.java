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

package dslabs.framework.testing.runner;

import com.google.common.collect.Iterables;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import dslabs.framework.testing.AbstractState;
import dslabs.framework.testing.ClientWorker;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.runner.Network.Inbox;
import dslabs.framework.testing.utils.Cloning;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.lang3.tuple.Pair;

@Log
@ToString(callSuper = true)
public class RunState extends AbstractState {
    @Getter private final Network network = new Network();

    private volatile RunSettings settings;

    /**
     * Whether or not an exception has been thrown during the handling of any
     * message or timer.
     */
    @Getter private volatile boolean exceptionThrown = false;

    // All accesses to these variables must be protected by synchronized(this)
    private Thread mainThread;
    private final Map<Address, Thread> nodeThreads = new HashMap<>();
    private long startTimeMillis;
    private boolean running = false, shuttingDown = false;

    // TODO: memoize important settings (e.g. multithreaded) at start time to
    //       ensure safety (even though they should never be modified)

    // TODO: break up synchronization a bit

    public RunState(Set<Address> servers, Set<Address> clientWorkers,
                    Set<Address> clients, StateGenerator stateGenerator) {
        super(servers, clientWorkers, clients, stateGenerator);
    }

    public RunState(Set<Address> servers, Set<Address> clientWorkers,
                    StateGenerator stateGenerator) {
        this(servers, clientWorkers, Collections.emptySet(), stateGenerator);
    }

    public RunState(StateGenerator stateGenerator) {
        this(Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), stateGenerator);
    }

    @Override
    protected synchronized void setupNode(Address address) {
        final Node node = node(address);
        final Inbox inbox = network.inbox(address);

        node.config(me -> {
            // Clone on message send
            Message m = Cloning.clone(me.getRight());
            network.send(new MessageEnvelope(me.getLeft(), me.getMiddle(), m));
        }, null, te -> {
            // Clone timer on set
            Timer t = Cloning.clone(te.getMiddle());
            Pair<Integer, Integer> bounds = te.getRight();
            inbox.set(new TimerEnvelope(te.getLeft(), t, bounds.getLeft(),
                    bounds.getRight()));
        }, e -> {
            exceptionThrown = true;
        }, true);
        node.init();

        // If we're already running in multi-threaded mode start the new node
        if (running && !shuttingDown && settings.multiThreaded()) {
            startNodeThread(address);
        }
    }

    @Override
    protected synchronized void cleanupNode(Address address)
            throws InterruptedException {
        while (nodeThreads.containsKey(address)) {
            nodeThreads.get(address).interrupt();
            wait();
        }
        network.removeInbox(address);
    }

    private void runNode(Address address, Node node, Inbox inbox) {
        while (!Thread.interrupted()) {
            Event item;
            try {
                item = inbox.take();
            } catch (InterruptedException e) {
                break;
            }

            if (item.isMessage()) {
                MessageEnvelope me = item.message();
                if (settings.shouldDeliver(me)) {
                    node.handleMessage(me.message(), me.from(), me.to());
                }
            }

            if (item.isTimer()) {
                TimerEnvelope te = item.timer();
                if (settings.deliverTimers()) {
                    node.onTimer(te.timer(), te.to());
                }
            }

            Thread.yield();
        }

        // Remove this thread from execution pool and notify cleanups
        synchronized (this) {
            nodeThreads.remove(address);
            notifyAll();
        }
    }

    private synchronized void takeSingleThreadedStep() {
        // Deliver 1 message and 1 timer per node
        for (Address address : addresses()) {
            Node node = node(address);
            Inbox inbox = network.inbox(address);

            MessageEnvelope me = inbox.pollMessage();
            if (me != null && settings.shouldDeliver(me)) {
                node.handleMessage(me.message(), me.from(), me.to());
            }

            TimerEnvelope te = inbox.pollTimer();
            if (te != null && settings.deliverTimers()) {
                node.onTimer(te.timer(), te.to());
            }
        }
    }

    // TODO: bring back waitForClientWorkers method
    // TODO: add waitForAndStop methods?

    /**
     * Waits for the run to finish. Should not be called concurrently with any
     * adds or removes. If the runState is shutdown concurrently, this thread
     * will not necessarily return and could wait indefinitely.
     *
     * @throws InterruptedException
     *         if interrupted while waiting
     */
    public void waitFor() throws InterruptedException {
        if (settings.timeLimited() && settings.waitForClients() &&
                Iterables.size(clientWorkers()) > 0) {
            for (ClientWorker c : clientWorkers()) {
                long timeLeft = timeLeftMillis();
                if (timeLeft > 0) {
                    c.waitUntilDone(timeLeft);
                }
            }
        } else if (settings.timeLimited()) {
            long timeLeft = timeLeftMillis();
            if (timeLeft > 0) {
                Thread.sleep(timeLeft);
            }
        } else if (settings.waitForClients() &&
                Iterables.size(clientWorkers()) > 0) {
            for (ClientWorker c : clientWorkers()) {
                c.waitUntilDone();
            }
        } else {
            while (true) {
                Thread.sleep(Long.MAX_VALUE);
            }
        }
    }

    private long timeLeftMillis() {
        return (startTimeMillis + settings.maxTimeSecs() * 1000) -
                System.currentTimeMillis();
    }

    public void run(RunSettings settings) throws InterruptedException {
        if (settings == null) {
            settings = new RunSettings();
        }

        if (settings.multiThreaded()) {
            if (startInternal(settings)) {
                waitFor();
                stop();
            }
        } else {
            // Run in single-threaded mode
            synchronized (this) {
                if (running) {
                    LOG.warning(
                            "Cannot run state, either currently running or not yet shutdown completely");
                    return;
                }

                this.running = true;
                this.settings = settings;
                this.startTimeMillis = System.currentTimeMillis();
                this.mainThread = Thread.currentThread();
            }

            boolean done = false;
            while (!done) {
                // TODO: make this and the other single-threaded loop more
                //       efficient? (don't lock every time, etc.)
                synchronized (this) {
                    takeSingleThreadedStep();

                    done = Thread.interrupted() || (settings.waitForClients() &&
                            Iterables.size(clientWorkers()) > 0 &&
                            clientWorkersDone()) ||
                            settings.timeUp(startTimeMillis);
                }
            }

            synchronized (this) {
                // If there's a shutdown effort ongoing, let it reset running
                if (!shuttingDown) {
                    this.running = false;
                }
                this.mainThread = null;
                notifyAll();
            }
        }
    }

    public void start(RunSettings settings) {
        startInternal(settings);
    }

    private synchronized boolean startInternal(RunSettings settings) {
        if (settings == null) {
            settings = new RunSettings();
        }

        if (running) {
            LOG.warning(
                    "Cannot start state, either currently running or not yet shutdown completely");
            return false;
        }

        this.settings = settings;
        this.running = true;
        this.startTimeMillis = System.currentTimeMillis();

        if (this.settings.multiThreaded()) {
            for (Address address : addresses()) {
                startNodeThread(address);
            }
        } else {
            mainThread = new Thread(() -> {
                while (!Thread.interrupted()) {
                    takeSingleThreadedStep();
                }

                synchronized (this) {
                    mainThread = null;
                    notifyAll();
                }
            }, "RunState: main");
            mainThread.start();
        }

        return true;
    }

    /**
     * Must be synchronized and running in multi-threaded mode when called.
     *
     * Starts a thread for a given node and adds it to runningThreads.
     *
     * @param address
     *         the address of the node to start
     */
    private void startNodeThread(Address address) {
        Thread t = new Thread(
                () -> runNode(address, node(address), network.inbox(address)),
                "RunState: " + address);
        nodeThreads.put(address, t);
        t.start();
    }

    /**
     * Stops running the system (waiting for it to fully stop). If this thread
     * is interrupted, does not ensure a full shutdown, only initiates one.
     */
    public synchronized void stop() throws InterruptedException {
        // Don't allow simultaneous stops
        while (shuttingDown) {
            wait();
        }

        shuttingDown = true;

        // Interrupt all threads
        if (mainThread != null) {
            mainThread.interrupt();
        }
        for (Thread t : nodeThreads.values()) {
            t.interrupt();
        }

        // Wait on all threads
        try {
            while (mainThread != null || !nodeThreads.isEmpty()) {
                wait();
            }
        } finally {
            shuttingDown = false;
            notifyAll();
        }

        running = false;
    }


    @Override
    public Iterable<TimerEnvelope> timers(Address address) {
        return network.inbox(address).timers();
    }

    @Override
    public synchronized <C extends Node & Client> Iterable<C> clients() {
        return super.clients();
    }

    @Override
    public synchronized <C extends Node & Client> C addClient(Address address) {
        return super.addClient(address);
    }

    @Override
    public <C extends Node & Client> C client(Address address) {
        return super.client(address);
    }

    @Override
    protected void ensureNodeConfig(Address address) {
    }
}
