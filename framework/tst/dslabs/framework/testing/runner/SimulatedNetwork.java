package dslabs.framework.testing.runner;

import dslabs.framework.Address;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.runner.Network.Inbox;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;
import javax.annotation.Nonnull;
import lombok.extern.java.Log;

/**
 * A deterministic simulated network.
 */
// the inheritance is for easily reusing `RunState`. consider extract common
// behavior of `Network` and `SimulatedNetwork` into interface(s)
@Log
public class SimulatedNetwork extends Network {
    @Override
    @Nonnull
    public Iterator<MessageEnvelope> iterator() {
        throw new UnsupportedOperationException(); // TODO
    }

    static class VirtualTimeEvent implements Comparable<VirtualTimeEvent> {
        long virtualTimeNanos;
        Event inner;

        @Override
        public int compareTo(VirtualTimeEvent o) {
            return Long.compare(virtualTimeNanos, o.virtualTimeNanos);
        }
    }

    long virtualTimeNanos = 0;
    long timeNanos = System.nanoTime();
    boolean autoAdvance;
    PriorityQueue<VirtualTimeEvent> events = new PriorityQueue<>();
    HashMap<Address, Integer> numMessage = new HashMap<>();

    Random rand = new Random();

    @Override
    public void send(MessageEnvelope messageEnvelope) {
        var event = new VirtualTimeEvent();
        event.virtualTimeNanos = (long) (virtualTimeNanos +
                (38 * 1000 + 20 * 1000 * rand.nextGaussian()));
        event.inner = new Event(messageEnvelope);
        synchronized (this) {
            events.add(event);
            var n = numMessage.getOrDefault(messageEnvelope.to(), 0) + 1;
            numMessage.put(messageEnvelope.to(), n);
        }
    }

    void set(TimerEnvelope timerEnvelope) {
        var event = new VirtualTimeEvent();
        event.virtualTimeNanos = (long) (virtualTimeNanos +
                (double) timerEnvelope.timerLengthMillis() * 1000 * 1000 +
                (20 * 1000 * rand.nextGaussian()));
        event.inner = new Event(timerEnvelope);
        synchronized (this) {
            events.add(event);
        }
    }

    synchronized void advanceVirtualTime() {
        long advanceInterval;
        if (autoAdvance) {
            var nextEvent = events.peek();
            if (nextEvent == null) {
                // TODO properly handle
                //                throw new RuntimeException();
                advanceInterval = 0;
            } else {
                advanceInterval = nextEvent.virtualTimeNanos - virtualTimeNanos;
            }
        } else {
            var timeNanos = System.nanoTime();
            assert timeNanos >= this.timeNanos;
            advanceInterval = timeNanos - this.timeNanos;
            this.timeNanos = timeNanos;

            var nextEvent = events.peek();
            if (nextEvent != null) {
                advanceInterval = Long.min(advanceInterval,
                        nextEvent.virtualTimeNanos - virtualTimeNanos);
            }
        }
        virtualTimeNanos += advanceInterval;
    }

    MessageEnvelope pollMessage(Address address) {
        synchronized (this) {
            advanceVirtualTime();

            var event = events.peek();
            if (event == null || event.virtualTimeNanos > virtualTimeNanos) {
                return null;
            }
            if (!event.inner.isMessage()) {
                return null;
            }
            if (!Objects.equals(address, event.inner.message().to())) {
                return null;
            }
            return Objects.requireNonNull(events.poll()).inner.message();
        }
    }

    TimerEnvelope pollTimer(Address address) {
        synchronized (this) {
            advanceVirtualTime();

            var event = events.peek();
            if (event == null || event.virtualTimeNanos > virtualTimeNanos) {
                return null;
            }
            if (!event.inner.isTimer()) {
                return null;
            }
            if (!Objects.equals(address, event.inner.timer().to())) {
                return null;
            }
            return Objects.requireNonNull(events.poll()).inner.timer();
        }
    }

    @Override
    public int numMessagesSentTo(Address address) {
        synchronized (this) {
            return numMessage.getOrDefault(address, 0);
        }
    }

    @Override
    public Event take(Address address) throws InterruptedException {
        throw new UnsupportedOperationException();
    }

    static class Inbox extends Network.Inbox {
        SimulatedNetwork network;
        Address address;

        @Override
        void send(MessageEnvelope m) {
            network.send(m);
        }

        @Override
        void set(TimerEnvelope t) {
            network.set(t);
        }

        @Override
        MessageEnvelope pollMessage() {
            return network.pollMessage(address);
        }

        @Override
        TimerEnvelope pollTimer() {
            return network.pollTimer(address);
        }

        @Override
        Event take() throws InterruptedException {
            return network.take(address);
        }

        @Override
        int numMessagesReceived() {
            return network.numMessagesSentTo(address);
        }

        @Override
        Collection<MessageEnvelope> messages() {
            throw new UnsupportedOperationException();
        }

        @Override
        Collection<TimerEnvelope> timers() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    Network.Inbox inbox(Address address) {
        var inbox = new Inbox();
        inbox.network = this;
        inbox.address = address;
        return inbox;
    }

    @Override
    public void removeInbox(Address address) {
    }
}
