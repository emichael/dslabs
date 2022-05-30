/*
 * Copyright (c) 2022 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework.testing.visualization;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dslabs.framework.Address;
import dslabs.framework.testing.AbstractState;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.search.SearchState;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Delegate;

class EventTreeState implements Serializable {
    private static final AtomicLong EVENT_NUMBER = new AtomicLong();

    // Extra methods from SearchState for Lombok to delegate
    private interface StateDelegate {
        Event previousEvent();

        boolean canStepTimer(TimerEnvelope timer);

        Throwable thrownException();

        // int depth();
    }

    @Delegate(types = {AbstractState.class, StateDelegate.class})
    @Getter(AccessLevel.PACKAGE) private final SearchState state;

    @Getter(AccessLevel.PACKAGE) private final EventTreeState parent;
    private final Map<Event, EventTreeState> children = new HashMap<>();

    private int height = 0;

    private final long eventNumber = EVENT_NUMBER.getAndIncrement();

    @Getter(AccessLevel.PACKAGE) private final Set<MessageEnvelope>
            undeliveredMessages;

    static EventTreeState convert(@NonNull SearchState searchState) {
        EventTreeState parent = null;
        for (SearchState s : searchState.trace()) {
            parent = new EventTreeState(s, parent);
        }
        assert parent != null;
        return parent;
    }

    private EventTreeState(@NonNull SearchState state, EventTreeState parent) {
        this.state = state;
        this.parent = parent;
        if (parent != null) {
            final Event e = previousEvent();
            assert e != null;
            Set<MessageEnvelope> temp =
                    new HashSet<>(parent.undeliveredMessages);
            // Remove this message from the message list
            if (e.isMessage()) {
                temp.remove(e.message());
            }
            temp.addAll(state.newMessages());
            undeliveredMessages = ImmutableSet.copyOf(temp);

            assert !parent.children.containsKey(previousEvent());
            parent.children.put(previousEvent(), this);
            parent.updateHeight();
        } else {
            undeliveredMessages = ImmutableSet.copyOf(state.network());
        }
    }

    EventTreeState step(final Event e) {
        if (children.containsKey(e)) {
            return children.get(e);
        }

        final SearchState next = state.stepEvent(e, null, true);
        assert next != null;
        return new EventTreeState(next, this);
    }

    private void updateHeight() {
        int oldHeight = height;
        height = children.values().stream()
                         .reduce(0, (i, e) -> Integer.max(i, e.height + 1),
                                 Integer::max);
        if (oldHeight != height && parent != null) {
            parent.updateHeight();
        }
    }

    Map<TimerEnvelope, Integer> timerFrequencies(Address address) {
        Map<TimerEnvelope, Integer> ret = new HashMap<>();
        for (TimerEnvelope t : state.timers(address)) {
            ret.compute(t, (__, i) -> i == null ? 1 : i + 1);
        }
        return ret;
    }

    @Getter(value = AccessLevel.PRIVATE,
            lazy = true) private final Set<MessageEnvelope> cachedNetwork =
            Sets.newHashSet(state.network());

    boolean messageIsNew(MessageEnvelope m, boolean viewDelieveredMessages) {
        if (parent == null) {
            return true;
        }

        if (viewDelieveredMessages) {
            assert cachedNetwork().contains(m);
            return !parent.cachedNetwork().contains(m);
        }

        assert undeliveredMessages.contains(m);
        return !parent.undeliveredMessages.contains(m);
    }

    boolean isInitialState() {
        return parent == null;
    }

    /**
     * Includes this state as the final element.
     */
    List<EventTreeState> pathFromInitial() {
        LinkedList<EventTreeState> trace = new LinkedList<>();
        for (EventTreeState s = this; s != null; s = s.parent) {
            trace.add(s);
        }
        Collections.reverse(trace);
        return trace;
    }

    List<EventTreeState> pathToBestDescendent() {
        List<EventTreeState> path = new LinkedList<>();
        for (EventTreeState e = bestChild(); e != null; e = e.bestChild()) {
            path.add(e);
        }
        return path;
    }

    private EventTreeState bestChild() {
        return children.values().stream().reduce(null, (e1, e2) -> {
            if (e1 == null) {
                return e2;
            }
            if (e2 == null) {
                return e1;
            }
            if (e1.eventNumber < e2.eventNumber) {
                return e1;
            }
            return e2;
        });
    }

    /**
     * Whether the trace leading to this state requires already delivered
     * messages to be re-delivered.
     *
     * @return whether duplicates are necessary
     */
    boolean sendsDeliveredMessages() {
        for (EventTreeState state : pathFromInitial()) {
            if (state.isInitialState()) {
                continue;
            }
            Event e = state.previousEvent();
            if (e.isMessage() && !state.parent().undeliveredMessages()
                                       .contains(e.message())) {
                return true;
            }
        }
        return false;
    }
}
