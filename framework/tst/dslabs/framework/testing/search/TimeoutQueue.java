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

package dslabs.framework.testing.search;

import dslabs.framework.testing.TimeoutEnvelope;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.Nonnull;
import lombok.EqualsAndHashCode;

/**
 * Implements an abstract timeout queue for a single node. In an asynchronous
 * system, the only restriction on timeout delivery is the following: if a node
 * sets timeouts t1, t2 in that order, and t2.duration >= t1.duration, then it
 * must deliver t1 before t2.
 *
 * This datastructure is not threadsafe.
 *
 * TODO: make this datastructure more efficient? Better way of representing?
 *
 * TODO: make equality checking a bit better? This technically returns false
 * sometimes when the actual queg sues (the dependency graphs) are identical. It
 * turns out, though, that doing the actual equality checking is a case of
 * DAG-isomorphism, which is Graph-isomorphism complete. It might be a special
 * case, but probably not. There might exist some relatively simple algorithm
 * for doing it, and since the timeout queues are relatively small, it might be
 * worth it. Anything that can reduce the branching factor of the BFS is good.
 */
@EqualsAndHashCode
class TimeoutQueue implements Serializable, Iterable<TimeoutEnvelope> {
    private final List<TimeoutEnvelope> timeouts;

    TimeoutQueue() {
        this.timeouts = new LinkedList<>();
    }

    /**
     * Creates a copy of the other TimeoutQueue.
     *
     * TODO: maybe add in a delayed copy mechanism?
     */
    TimeoutQueue(TimeoutQueue other) {
        this.timeouts = new LinkedList<>(other.timeouts);
    }

    void add(TimeoutEnvelope timeoutEnvelope) {
        timeouts.add(timeoutEnvelope);
    }

    Iterable<TimeoutEnvelope> deliverable() {
        return new Iterable<TimeoutEnvelope>() {
            @Override
            @Nonnull
            public Iterator<TimeoutEnvelope> iterator() {
                return new Iterator<TimeoutEnvelope>() {
                    Integer minMaxTime = null;
                    int i = 0;

                    private void skip() {
                        while (i < timeouts.size() && minMaxTime != null &&
                                timeouts.get(i).minTimeoutLengthMillis() >=
                                        minMaxTime) {
                            i++;
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        return i < timeouts.size();
                    }

                    @Override
                    public TimeoutEnvelope next()
                            throws NoSuchElementException {
                        if (hasNext()) {
                            TimeoutEnvelope next = timeouts.get(i);
                            i++;
                            if (minMaxTime == null ||
                                    next.maxTimeoutLengthMillis() <
                                            minMaxTime) {
                                minMaxTime = next.maxTimeoutLengthMillis();
                            }
                            skip();
                            return next;
                        } else {
                            throw new NoSuchElementException();
                        }
                    }
                };
            }
        };
    }

    boolean isDeliverable(TimeoutEnvelope timeoutEnvelope) {
        for (TimeoutEnvelope te : timeouts) {
            if (te.equals(timeoutEnvelope)) {
                return true;
            }
            if (timeoutEnvelope.minTimeoutLengthMillis() >=
                    te.maxTimeoutLengthMillis()) {
                return false;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return timeouts.toString();
    }

    @Override
    @Nonnull
    public Iterator<TimeoutEnvelope> iterator() {
        return timeouts.iterator();
    }

    void remove(TimeoutEnvelope timeoutEnvelope) {
        timeouts.remove(timeoutEnvelope);
    }
}
