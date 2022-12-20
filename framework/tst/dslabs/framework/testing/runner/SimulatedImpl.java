package dslabs.framework.testing.runner;

import java.util.PriorityQueue;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Iterables;

import dslabs.framework.Node;
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
    long nowNanos = 0l;
    Random rand = new Random(GlobalSettings.seed());

    Thread simulateThread;

    SimulatedImpl(RunState state) {
        this.state = state;
    }

    void setupNode(Node node) {
        node.config(me_ -> {
            var timeNanos = nowNanos + 7500 * 1000 + (long) (2000 * 1000 * rand.nextGaussian());
            if (timeNanos <= nowNanos) {
                timeNanos = nowNanos + 1;
            }
            var me = new MessageEnvelope(me_.getLeft(), me_.getMiddle(), Cloning.clone(me_.getRight()));
            events.add(Pair.of(timeNanos, me));
        }, null, te_ -> {
            var te = new TimerEnvelope(te_.getLeft(), Cloning.clone(te_.getMiddle()), te_.getRight().getLeft(),
                    te_.getRight().getRight());
            var timeNanos = nowNanos + te.timerLengthMillis() * 1000 * 1000 + (long) (64 * rand.nextGaussian());
            // timer triggering is managed by `SimulatedImpl` instead of `Network`
            // make sure timer is due as soon as pushing into `Network` like message
            te = new TimerEnvelope(te.to(), te.timer(), 0, 0);
            events.add(Pair.of(timeNanos, te));
        }, e -> {
            // TODO
        }, true);
        node.init();
    }

    // we make a very "isolated" assumption on `run`'s use case
    // we are out of sync with `RunState`, `Network` and any other component from outside world
    void run(RunSettings settings) throws InterruptedException {
        var clientWorkersDone = false;
        while (!Thread.interrupted() && !clientWorkersDone) {
            if (!dispatchNextEvent(settings)) {
                break;
            }
            clientWorkersDone = settings.waitForClients() && Iterables.size(state.clientWorkers()) > 0
                    && state.clientWorkersDone();
        }
    }

    boolean dispatchNextEvent(RunSettings settings) {
        var virtualEvent = events.poll();
        assert virtualEvent != null; //
        nowNanos = virtualEvent.getLeft();
        if (settings.timeLimited() && nowNanos >= (long) settings.maxTimeSecs() * 1000 * 1000 * 1000) {
            return false;
        }
        var event = virtualEvent.getRight();

        var logLine = String.format("%.6f ms in simulation (noninteractive)", (float) nowNanos / 1000 / 1000);
        if (event instanceof MessageEnvelope) {
            var me = (MessageEnvelope) event;
            if (settings.shouldDeliver(me, rand)) {
                LOG.finer(logLine);
                state.node(me.to()).handleMessage(me.message(), me.from(), me.to());
            }
        } else if (event instanceof TimerEnvelope) {
            var te = (TimerEnvelope) event;
            if (settings.deliverTimers()) {
                LOG.finer(logLine);
                state.node(te.to()).onTimer(te.timer(), te.to());
            }
        } else {
            throw new RuntimeException("unreachable");
        }
        return true;
    }

    void start(RunSettings settings) {
        assert simulateThread == null;
        var interactiveThread = Thread.currentThread();
        simulateThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                // assert interactive thread's valid state?
                if (interactiveThread.getState() != Thread.State.WAITING) {
                    Thread.onSpinWait();
                    continue;
                }
                var dispatched = dispatchNextEvent(settings);
                // `start` should never be used with a time-limited settings right?
                assert dispatched;
            }
        });
        simulateThread.start();
    }

    void stop() throws InterruptedException {
        if (simulateThread != null) {
            simulateThread.interrupt();
            simulateThread.join();
            simulateThread = null;
        }
    }
}
