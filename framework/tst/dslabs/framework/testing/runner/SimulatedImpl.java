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
    long nowNanos = 0l, systemNanos = System.nanoTime();
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

    long messageLatencyNanos() {
        var timeNanos = 50 * 1000 + (long) (15 * 1000 * rand.nextGaussian());
        return Long.max(timeNanos, 1);
    }

    // in my implementation a 30ms threshold covers all processing except GC tests
    // if necessary, deterministic modeling of longer processing time can be introduced
    long processTimeNanos() {
        var nanos = (System.nanoTime() - systemNanos);
        // intentionally, simulated processing times is scaled down to [0, 1.2ms)
        // from [0, 30ms), to model unevenly-distributed realistic processing
        // latency
        // use a Poission distribution to properly model the long-tailing
        // behavior if applicable
        if (nanos < 30 * 1000 * 1000) {
            return rand.nextLong(0, 1200 * 1000);
        }
        LOG.warning(() -> String.format(
                "Long process time (%.6fms) is taken into account which causes non-deterministic simulation",
                (float) nanos / 1000 / 1000));
        return nanos;
    }

    void setupNode(Node node) {
        // hacky but not too hacky
        if (node instanceof ClientWorker) {
            ((ClientWorker) node).simulatedImpl(this);
        }
        node.config(me_ -> {
            var me = new MessageEnvelope(me_.getLeft(), me_.getMiddle(), Cloning.clone(me_.getRight()));
            var processTimeNanos = processTimeNanos();
            var latency = messageLatencyNanos();
            var timeNanos = nowNanos + processTimeNanos + latency;
            LOG.finest(() -> String.format(
                    " ... will happen at %.6fms = %.6fms (now) + %.6fms (processed) + %.6fms (message latency)",
                    (float) timeNanos / 1000 / 1000, (float) nowNanos / 1000 / 1000,
                    (float) processTimeNanos / 1000 / 1000, (float) latency / 1000 / 1000));
            events.add(Pair.of(timeNanos, me));
        }, null, te_ -> {
            var te = new TimerEnvelope(te_.getLeft(), Cloning.clone(te_.getMiddle()), te_.getRight().getLeft(),
                    te_.getRight().getRight());
            var lengthNanos = te.timerLengthMillis() * 1000 * 1000 + (long) (64 * rand.nextGaussian());
            var processTimeNanos = processTimeNanos();
            var timeNanos = nowNanos + processTimeNanos + lengthNanos;
            LOG.finest(() -> String.format(
                    " ... will happen at %.6fms = %.6fms (now) + %.6fms (processed) + %.6fms (timer length)",
                    (float) timeNanos / 1000 / 1000, (float) nowNanos / 1000 / 1000,
                    (float) processTimeNanos / 1000 / 1000, (float) lengthNanos / 1000 / 1000));
            events.add(Pair.of(timeNanos, te));
        }, e -> {
            // TODO
        }, true);

        systemNanos = System.nanoTime();
        node.init();
    }

    void run(RunSettings settings) {
        running = true;
        while (!Thread.interrupted() && !isDone(settings)) {
            dispatchNextEvent(settings);
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
        Supplier<String> logLine = () -> String.format("%.6f ms in simulation ...", (float) nowNanos / 1000 / 1000);
        var event = virtualEvent.getValue();

        // set process time baseline for everything will happen before next `dispatchNextEvent`
        // including node's reaction message/timer during handling message/timer,
        // interactive threads triggered message/timer (probably on `Client`) if 
        // they get `notify`ed, then `sendCommand`, or `add*` and trigger `init`
        // everything mentioned above is possible to be slow
        systemNanos = System.nanoTime();

        if (event instanceof MessageEnvelope) {
            var me = (MessageEnvelope) event;
            var address = me.to().rootAddress();
            numMessagesSendTo.put(address, numMessagesSendTo.getOrDefault(address, 0) + 1);
            if (settings.shouldDeliver(me)) {
                // in lab 2, test server is never `addServer`ed
                // so we have to check for `take` before checking removal
                if (checkTake(address, new Event(me))) {
                    LOG.finer(() -> logLine.get() + " (taken)");
                } else if (state.hasNode(address)) {
                    LOG.finer(logLine);
                    state.node(address).handleMessage(me.message(), me.from(), me.to());
                }
            }
        } else if (event instanceof TimerEnvelope) {
            var te = (TimerEnvelope) event;
            var address = te.to().rootAddress();
            if (settings.deliverTimers()) {
                if (checkTake(address, new Event(te))) {
                    LOG.finer(() -> logLine.get() + " (taken)");
                } else if (state.hasNode(address)) {
                    LOG.finer(logLine);
                    state.node(address).onTimer(te.timer(), te.to());
                }
            }
        } else {
            LOG.finer(() -> logLine.get() + " (interactive thread wake up)");
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
            interactiveThreads.add(Thread.currentThread());
        }
        simulateThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                if (interactiveThreadRunning()) {
                    // i hope there's a better way to wait until every other thread `WAITING`
                    Thread.onSpinWait();
                    continue;
                }
                // interactive thread just called `stop` and `join`ing this thread
                // consider make the structure more elegant
                if (Thread.interrupted()) {
                    break;
                }

                // lab 2 part 2 test 10 concurrent put
                // during "kill the primary", "client-readprimary" is already 
                // done and cause `isDone` returns true
                // so i guess this assertion does not need to hold as long as
                // no interactive thread call `waitFor`
                // consider assert it when there is `waitFor` calling
                // assert !isDone(settings);
                dispatchNextEvent(settings);
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
        // LOG.info("Stop");
        if (simulateThread != null) {
            simulateThread.interrupt();
            simulateThread.join();
            simulateThread = null;
        }
        synchronized (interactiveThreads) {
            interactiveThreads.remove(Thread.currentThread());
        }
        running = false;
        // LOG.info("Stop done");
    }

    void sleep(long millis) throws InterruptedException {
        var monitor = new Object();
        events.add(Pair.of(nowNanos + millis * 1000 * 1000, monitor));
        synchronized (monitor) {
            monitor.wait();
        }
    }

    void startThread(Runnable runnable) {
        // every lab's test 1 "Client throws InterruptedException" voilate this
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
            waitForMonitor.wait();
        }
    }

    Event take(Address address) throws InterruptedException {
        synchronized (pendingTake) {
            // we don't suppose to support concurrent `take` right?
            assert pendingTake.getKey() == null;
            pendingTake.setLeft(address);
            pendingTake.setValue(null);
            pendingTake.wait();
            return pendingTake.getValue();
        }
    }

    void send(MessageEnvelope messageEnvelope) {
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