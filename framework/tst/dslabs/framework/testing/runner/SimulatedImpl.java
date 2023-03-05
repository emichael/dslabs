package dslabs.framework.testing.runner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Iterables;

import dslabs.framework.Address;
import dslabs.framework.Node;
import dslabs.framework.testing.ClientWorker;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.utils.Cloning;
import dslabs.framework.testing.utils.GlobalSettings;
import lombok.extern.java.Log;

@Log
public class SimulatedImpl {
    RunState state;
    // a little bit wild to put a `Object` here
    // consider create a proper sum type
    PriorityQueue<Pair<Long, Object>> events = new PriorityQueue<>((ve1, ve2) -> {
        return Long.compare(ve1.getKey(), ve2.getKey());
    });
    long nowNanos = 0l, processNanos = 0l, systemNanos = System.nanoTime();
    Random rand = new Random(GlobalSettings.rand().nextLong());
    Thread simulateThread;
    List<Thread> interactiveThreads = new ArrayList<>();
    final MutablePair<Address, Event> pendingTake = MutablePair.of(null, null);
    Map<Address, Integer> numMessagesSendTo = new HashMap<>();
    boolean running = false;
    final Object waitForMonitor = new Object();

    SimulatedImpl(RunState state) {
        this.state = state;
    }

    // time model guideline: make sure all below requirements are satisfied
    // * sufficient variant in message latency, make sure later-sent message can
    // arrive earlier with a properly-set process time
    // * in several baseline "benchmark tests" the number of finished requests
    // would be close to the result by running in real mode, i.e. within a
    // magnitude

    long messageLatencyNanos() {
        var timeNanos = 40 * 1000 + (long) (12 * 1000 * rand.nextGaussian());
        return Long.max(timeNanos, 1);
    }

    void measureProcess() {
        var nowNanos = System.nanoTime();
        var nanos = nowNanos - systemNanos;
        systemNanos = nowNanos;

        var threshold = 30 * 1000 * 1000;
        if (nanos >= threshold) {
            LOG.warning(() -> String.format(
                    "Long process time (%.6fms) differs a lot from simulation",
                    (float) nanos / 1000 / 1000));
        }

        // an exponential distribution with mean value `lambda` and "capped" at
        // `threshold`
        // cannot find a material about what to do a upper-bounded exponential
        // distribution properly (and efficiently),
        // so using reject sampling, effectively should upscale bounding range
        // proportionally
        var lambda = 1. / (10 * 1000);
        var processIncreamentNanos = Math.log(1 - rand.nextDouble()) / (-lambda);
        while (processIncreamentNanos >= threshold) {
            processIncreamentNanos = Math.log(1 - rand.nextDouble()) / (-lambda);
        }
        processNanos += processIncreamentNanos;
    }

    void setupNode(Node node) {
        // hacky but not too hacky
        if (node instanceof ClientWorker) {
            ((ClientWorker) node).simulatedImpl(this);
        }
        node.config(me_ -> {
            measureProcess();
            var me = new MessageEnvelope(me_.getLeft(), me_.getMiddle(), Cloning.clone(me_.getRight()));
            var latency = messageLatencyNanos();
            var timeNanos = nowNanos + processNanos + latency;
            LOG.finest(() -> String.format(
                    " ... will happen at %.6fms = %.6fms (now) + %.6fms (processed) + %.6fms (message latency)",
                    (float) timeNanos / 1000 / 1000, (float) nowNanos / 1000 / 1000,
                    (float) processNanos / 1000 / 1000, (float) latency / 1000 / 1000));
            events.add(Pair.of(timeNanos, me));
        }, null, te_ -> {
            measureProcess();
            var te = new TimerEnvelope(te_.getLeft(), Cloning.clone(te_.getMiddle()), te_.getRight().getLeft(),
                    te_.getRight().getRight());
            var lengthNanos = te.timerLengthMillis() * 1000 * 1000;
            var timeNanos = nowNanos + processNanos + lengthNanos;
            LOG.finest(() -> String.format(
                    " ... will happen at %.6fms = %.6fms (now) + %.6fms (processed) + %.6fms (timer length)",
                    (float) timeNanos / 1000 / 1000, (float) nowNanos / 1000 / 1000,
                    (float) processNanos / 1000 / 1000, (float) lengthNanos / 1000 / 1000));
            events.add(Pair.of(timeNanos, te));
        }, e -> {
            state.exceptionThrown(true);
        }, true);

        node.init();
    }

    void run(RunSettings settings) {
        running = true;
        while (!Thread.interrupted() && !isDone(settings)) {
            dispatchNextEvent(settings);
            if (state.exceptionThrown()) {
                throw new AssertionError("Exception thrown by running nodes.");
            }
        }
        running = false;
    }

    boolean isDone(RunSettings settings) {
        if (settings.timeLimited() && events.peek().getKey() >= (long) settings.maxTimeSecs() * 1000 * 1000 * 1000) {
            return true;
        }
        if (settings.waitForClients() && Iterables.size(state.clientWorkers()) > 0 && state.clientWorkersDone()) {
            return true;
        }
        return false;
    }

    void dispatchNextEvent(RunSettings settings) {
        var virtualEvent = events.poll();
        assert virtualEvent != null; //
        nowNanos = virtualEvent.getKey();
        processNanos = 0;
        Supplier<String> logLine = () -> String.format("%.6f ms in simulation ...", (float) nowNanos / 1000 / 1000);
        var event = virtualEvent.getValue();

        if (event instanceof MessageEnvelope) {
            var me = (MessageEnvelope) event;
            var address = me.to().rootAddress();
            numMessagesSendTo.put(address, numMessagesSendTo.getOrDefault(address, 0) + 1);
            if (settings.shouldDeliver(me)) {
                // in lab 2, test server is never `addServer`ed
                // so we have to check for `take` before checking removal
                if (checkTake(address, new Event(me))) {
                    LOG.finest(() -> logLine.get() + " (taken)");
                } else if (state.hasNode(address)) {
                    LOG.finest(logLine);
                    state.node(address).handleMessage(me.message(), me.from(), me.to());
                    measureProcess(); // for the side effect to warn long process time
                }
            }
        } else if (event instanceof TimerEnvelope) {
            var te = (TimerEnvelope) event;
            var address = te.to().rootAddress();
            if (settings.deliverTimers()) {
                if (checkTake(address, new Event(te))) {
                    LOG.finest(() -> logLine.get() + " (taken)");
                } else if (state.hasNode(address)) {
                    LOG.finest(logLine);
                    state.node(address).onTimer(te.timer(), te.to());
                    measureProcess(); // same as above
                }
            }
        } else {
            LOG.finest(() -> logLine.get() + " (interactive thread wake up)");
            synchronized (event) {
                event.notify();
            }
        }
    }

    boolean checkTake(Address to, Event event) {
        synchronized (pendingTake) {
            if (to.equals(pendingTake.getKey())) {
                pendingTake.setLeft(null); // why there's no `setKey`?
                pendingTake.setValue(event);
                pendingTake.notify();
                return true;
            }
        }
        return false;
    }

    void start(RunSettings settings) {
        assert !running;
        running = true;
        assert simulateThread == null;
        synchronized (interactiveThreads) {
            interactiveThreads.add(0, Thread.currentThread());
        }
        simulateThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                // notes on reasoning about concurrency
                // `SimulateImpl` only modify states (of itself and of nodes)
                // inside `dispatchNextEvent` call, and when the call happens,
                // `simulateThread` is always the only thread that not `wait`
                // during `dispatchNextEvent`, interactive threads may get
                // `notify`ed for various reasons. `dispatchNextEvent` does not
                // modify states any more after any possible wake up
                // since the execution is temporal mutually execusive, it is not
                // necessary to set up sync region for any state. all
                // `synchronized` in this class is for `wait` and `notify`,
                // except `interactiveThreads`, which could be concurrently
                // accessed by interactive threads

                if (interactiveThreadRunning()) {
                    // i hope there's a better way to wait until every other thread `WAITING`
                    Thread.onSpinWait();
                    continue;
                }
                // interactive thread just called `stop` and `join`ing this thread
                // consider make the code logic more elegant
                if (Thread.interrupted()) {
                    break;
                }

                // lab 2 part 2 test 10 concurrent put
                // during "kill the primary", "client-readprimary" is already
                // done and cause `isDone` returns true
                // so i guess this assertion does not need to hold as long as
                // no interactive thread call `waitFor`
                // consider assert it only when there is `waitFor` calling
                //
                // assert !isDone(settings);
                dispatchNextEvent(settings);
                if (state.exceptionThrown()) {
                    // if test thread is `wait` inside `SimulatedImpl` it checks
                    // for interrupting cause correctly
                    // if it is `wait` outside, e.g. on `getResult`, we need to
                    // make sure this will always result in clear error message
                    interactiveThreads.get(0).interrupt();
                    return;
                }
                if (isDone(settings)) {
                    synchronized (waitForMonitor) {
                        waitForMonitor.notifyAll();
                    }
                }
            }
        });
        simulateThread.start();
    }

    boolean interactiveThreadRunning() {
        assert Thread.currentThread().equals(simulateThread);
        synchronized (interactiveThreads) {
            for (var thread : interactiveThreads) {
                var state = thread.getState();
                if (state == Thread.State.TERMINATED) {
                    // this is either because the thread called `start` exit
                    // without calling `stop`, or any interactive thread throw
                    // exception
                    // in either case simulation should not progress any more
                    //
                    // need more think on this
                    return true;
                }
                if (state != Thread.State.WAITING) {
                    // especially cannot be `TIMED_WAITING`
                    // simulation is not compatible with anything works with real world time
                    assert state == Thread.State.RUNNABLE || state == Thread.State.BLOCKED;
                    return true;
                }
            }
        }
        return false;
    }

    void stop() throws InterruptedException {
        if (!running) {
            return;
        }
        if (simulateThread != null) {
            simulateThread.interrupt();
            simulateThread.join();
            simulateThread = null;
        }
        synchronized (interactiveThreads) {
            interactiveThreads.remove(Thread.currentThread());
        }
        running = false;
    }

    void sleep(long millis) throws InterruptedException {
        assert running; // do not use this in interrupted tests
        var monitor = new Object();
        events.add(Pair.of(nowNanos + millis * 1000 * 1000, monitor));
        synchronized (monitor) {
            try {
                monitor.wait();
            } catch (InterruptedException e) {
                checkExceptionThrown();
                throw e;
            }
        }
    }

    void checkExceptionThrown() {
        if (Thread.currentThread().equals(interactiveThreads.get(0)) && state.exceptionThrown()) {
            throw new AssertionError("Exception thrown by running nodes.");
        }
    }

    void startThread(Runnable runnable) {
        // interrupted tests voilate this
        // assert running;
        var thread = new Thread(runnable);
        synchronized (interactiveThreads) {
            interactiveThreads.add(thread);
        }
        thread.start();
    }

    void shutdownStartedThreads() throws InterruptedException {
        synchronized (interactiveThreads) {
            for (var thread : interactiveThreads) {
                if (thread.equals(Thread.currentThread())) {
                    continue;
                }
                thread.interrupt();
                thread.join();
            }
            interactiveThreads.clear();
            if (running) {
                interactiveThreads.add(Thread.currentThread()); // careful
            }
        }
    }

    public long currentTimeMillis() {
        return nowNanos / 1000 / 1000;
    }

    void waitFor() throws InterruptedException {
        assert running;
        synchronized (waitForMonitor) {
            try {
                waitForMonitor.wait();
            } catch (InterruptedException e) {
                checkExceptionThrown();
                throw e;
            }
        }
    }

    Event take(Address address) throws InterruptedException {
        synchronized (pendingTake) {
            // we don't suppose to support concurrent `take` right?
            assert pendingTake.getKey() == null;
            pendingTake.setLeft(address);
            pendingTake.setValue(null);
            try {
                pendingTake.wait();
            } catch (InterruptedException e) {
                checkExceptionThrown();
                throw e;
            }
            return pendingTake.getValue();
        }
    }

    void send(MessageEnvelope messageEnvelope) {
        //
        events.add(Pair.of(nowNanos + messageLatencyNanos(), messageEnvelope));
    }

    int numMessagesSendTo(Address address) {
        return numMessagesSendTo.getOrDefault(address, 0);
    }
}

class SimulatedNetwork extends Network {
    SimulatedImpl impl;

    SimulatedNetwork(SimulatedImpl impl) {
        this.impl = impl;
    }

    @Override
    public Event take(Address address) throws InterruptedException {
        return impl.take(address);
    }

    @Override
    public void send(MessageEnvelope messageEnvelope) {
        impl.send(messageEnvelope);
    }

    @Override
    public int numMessagesSentTo(Address address) {
        return impl.numMessagesSendTo(address);
    }

    // defensively override all public methods
    @Override
    public void removeInbox(Address address) {
        throw new NotImplementedException();
    }

    @Override
    @Nonnull
    public Iterator<MessageEnvelope> iterator() {
        throw new NotImplementedException();
    }
}