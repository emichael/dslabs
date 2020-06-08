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

import dslabs.framework.Address;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import lombok.extern.java.Log;

/**
 * Simple implementation of a network object, safe for concurrent access.
 */
@Log
public class Network implements Iterable<MessageEnvelope> {
    static class Inbox {
        private static final long MIN_WAIT_TIME_NANOS = 1500000;

        private final Queue<MessageEnvelope> messages =
                new ConcurrentLinkedQueue<>();
        private final Queue<TimerEnvelope> timers =
                new PriorityBlockingQueue<>();

        // Reader thread state
        private volatile boolean waiting = false;
        private volatile long waitingEndTime = Long.MAX_VALUE;

        // Writer thread state
        private volatile boolean newMessageAvailable = false;
        private final AtomicLong newTimerEndTime =
                new AtomicLong(Long.MAX_VALUE);

        private final AtomicInteger numMessagesReceived = new AtomicInteger();

        void send(MessageEnvelope m) {
            messages.add(m);
            numMessagesReceived.incrementAndGet();

            newMessageAvailable = true;
            if (waiting) {
                waiting = false;
                synchronized (this) {
                    notify();
                }
            }
        }

        void set(TimerEnvelope t) {
            timers.add(t);

            long endTime = t.endTimeNanos();

            newTimerEndTime.accumulateAndGet(endTime, Long::min);
            if (waiting && endTime < waitingEndTime) {
                waiting = false;
                synchronized (this) {
                    notify();
                }
            }
        }

        MessageEnvelope pollMessage() {
            return messages.poll();
        }

        TimerEnvelope pollTimer() {
            TimerEnvelope te = timers.peek();
            if (te == null || !te.isDue()) {
                return null;
            }
            return timers.poll();
        }

        Event take() throws InterruptedException {
            while (true) {
                newTimerEndTime.set(Long.MAX_VALUE);
                TimerEnvelope te = timers.peek();
                if (te != null && te.isDue()) {
                    return new Event(timers.poll());
                }

                newMessageAvailable = false;
                MessageEnvelope me = messages.poll();
                if (me != null) {
                    return new Event(me);
                }

                // Wait for new message or timer
                if (te == null) {
                    synchronized (this) {
                        waiting = true;
                        try {
                            if (!newMessageAvailable &&
                                    newTimerEndTime.get() >= waitingEndTime) {
                                wait();
                            }
                        } finally {
                            waiting = false;
                        }
                    }

                } else {
                    // Deliver timers if they're close to being done
                    long endTime = te.endTimeNanos();
                    long waitTime = endTime - System.nanoTime();
                    if (waitTime <= MIN_WAIT_TIME_NANOS) {
                        return new Event(timers.poll());
                    }

                    synchronized (this) {
                        waiting = true;
                        waitingEndTime = endTime;
                        try {
                            if (!newMessageAvailable &&
                                    newTimerEndTime.get() >= waitingEndTime) {
                                wait(waitTime / 1000000,
                                        (int) (waitTime % 1000000));
                            }
                        } finally {
                            waiting = false;
                            waitingEndTime = Long.MAX_VALUE;
                        }
                    }

                }
            }
        }

        int numMessagesReceived() {
            return numMessagesReceived.get();
        }

        Collection<MessageEnvelope> messages() {
            return new LinkedList<>(messages);
        }

        Collection<TimerEnvelope> timers() {
            return new LinkedList<>(timers);
        }
    }


    private final Map<Address, Inbox> inboxes = new ConcurrentHashMap<>();

    Inbox inbox(Address address) {
        Inbox inbox;
        if ((inbox = inboxes.get(address)) != null) {
            return inbox;
        }
        return inboxes.computeIfAbsent(address, __ -> new Inbox());
    }

    public void removeInbox(Address address) {
        inboxes.remove(address);
    }

    public void send(MessageEnvelope messageEnvelope) {
        inbox(messageEnvelope.to().rootAddress()).send(messageEnvelope);
    }

    public int numMessagesSentTo(Address address) {
        return inbox(address.rootAddress()).numMessagesReceived();
    }

    @Override
    @Nonnull
    public Iterator<MessageEnvelope> iterator() {
        LinkedList<MessageEnvelope> messages = new LinkedList<>();
        for (Inbox inbox : inboxes.values()) {
            messages.addAll(inbox.messages());
        }
        return messages.iterator();
    }

    public Event take(Address address) throws InterruptedException {
        return inbox(address.rootAddress()).take();
    }
}
