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

import dslabs.framework.testing.TimerEnvelope;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import javax.annotation.Nonnull;
import lombok.EqualsAndHashCode;

/**
 * Implements an abstract timer queue for a single node. In an asynchronous
 * system, the only restriction on timer delivery is the following: if a node
 * sets timers t1, t2 in that order, and t2.minTimerLength >= t1.maxTimerLength,
 * then it must deliver t1 before t2.
 *
 * This datastructure is not threadsafe.
 *
 * TODO: make this datastructure more efficient? Better way of representing?
 *
 * TODO: equality checking is definitely wrong now
 */
@EqualsAndHashCode
class TimerQueue implements Serializable, Iterable<TimerEnvelope> {
    private final List<TimerEnvelope> timers;

    TimerQueue() {
        this.timers = new LinkedList<>();
    }

    /**
     * Creates a copy of the other TimerQueue.
     *
     * TODO: maybe add in a delayed copy mechanism?
     */
    TimerQueue(TimerQueue other) {
        this.timers = new LinkedList<>(other.timers);
    }

    void add(TimerEnvelope timerEnvelope) {
        timers.add(timerEnvelope);
    }

    Iterable<TimerEnvelope> deliverable() {
        return new Iterable<TimerEnvelope>() {
            @Override
            @Nonnull
            public Iterator<TimerEnvelope> iterator() {
                return new Iterator<TimerEnvelope>() {
                    Integer minMaxTime = null;
                    int i = 0;

                    private void skip() {
                        while (i < timers.size() && minMaxTime != null &&
                                timers.get(i).minTimerLengthMillis() >=
                                        minMaxTime) {
                            i++;
                        }
                    }

                    @Override
                    public boolean hasNext() {
                        return i < timers.size();
                    }

                    @Override
                    public TimerEnvelope next() throws NoSuchElementException {
                        if (hasNext()) {
                            TimerEnvelope next = timers.get(i);
                            i++;
                            if (minMaxTime == null ||
                                    next.maxTimerLengthMillis() < minMaxTime) {
                                minMaxTime = next.maxTimerLengthMillis();
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

    boolean isDeliverable(TimerEnvelope timerEnvelope) {
        // TODO: quadratic
        for (TimerEnvelope te : timers) {
            if (te.equals(timerEnvelope)) {
                return true;
            }
            if (timerEnvelope.minTimerLengthMillis() >=
                    te.maxTimerLengthMillis()) {
                return false;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return timers.toString();
    }

    @Override
    @Nonnull
    public Iterator<TimerEnvelope> iterator() {
        return timers.iterator();
    }

    void remove(TimerEnvelope timerEnvelope) {
        timers.remove(timerEnvelope);
    }
}
