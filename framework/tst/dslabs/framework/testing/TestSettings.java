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

package dslabs.framework.testing;

import dslabs.framework.Address;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Collection of settings used by various tests.
 *
 * Safe for concurrent access.
 */
@Getter
@Setter
public class TestSettings {
    /* Defaults */
    private static final int DEFAULT_TIME_LIMIT_SECS = 5;

    /* Settings */
    private final Collection<StatePredicate> invariants =
            new ConcurrentLinkedQueue<>();
    private final Collection<StatePredicate> prunes =
            new ConcurrentLinkedQueue<>();

    private volatile int maxTimeSecs = -1;
    private volatile boolean singleThreaded = GlobalSettings.singleThreaded();

    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE) private volatile boolean
            deliverTimers = true;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private final Map<Address, Boolean> timersActive =
            new ConcurrentHashMap<>();

    public final boolean deliverTimers() {
        return deliverTimers;
    }

    public final TestSettings deliverTimers(Address a, boolean b) {
        timersActive.put(a, b);
        return this;
    }

    public final TestSettings clearDeliverTimers() {
        deliverTimers = true;
        timersActive.clear();
        return this;
    }

    public final boolean deliverTimers(Address a) {
        return timersActive.getOrDefault(a, deliverTimers);
    }

    public final TestSettings deliverTimers(boolean b) {
        deliverTimers = b;
        return this;
    }


    // Network settings
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private final Map<Pair<Address, Address>, Boolean> linkActive =
            new ConcurrentHashMap<>();
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private final Map<Address, Boolean> senderActive =
            new ConcurrentHashMap<>();
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    private final Map<Address, Boolean> receiverActive =
            new ConcurrentHashMap<>();
    @Getter(AccessLevel.NONE) private volatile boolean networkActive = true;


    public final TestSettings addInvariant(StatePredicate invariant) {
        invariants.add(invariant);
        return this;
    }

    public final TestSettings clearInvariants() {
        invariants.clear();
        return this;
    }

    // TODO: move to SearchSettings
    public final TestSettings addPrune(StatePredicate prune) {
        prunes.add(prune);
        return this;
    }

    public final TestSettings clearPrunes() {
        prunes.clear();
        return this;
    }

    public final StatePredicate whichInvariantViolated(AbstractState state) {
        for (StatePredicate inv : invariants) {
            if (!inv.test(state)) {
                return inv;
            }
        }
        return null;
    }

    public final boolean invariantsHold(AbstractState state) {
        for (StatePredicate inv : invariants) {
            if (!inv.test(state)) {
                return false;
            }
        }
        return true;
    }

    public final boolean invariantViolated(AbstractState state) {
        return !invariantsHold(state);
    }

    public final boolean shouldPrune(AbstractState state) {
        for (StatePredicate prune : prunes) {
            if (prune.test(state)) {
                return true;
            }
        }
        return false;
    }


    public final boolean timeLimited() {
        return maxTimeSecs > 0;
    }

    public final void timeLimited(boolean timeLimited) {
        if (timeLimited) {
            maxTimeSecs = timeLimited() ? maxTimeSecs : DEFAULT_TIME_LIMIT_SECS;
        } else {
            maxTimeSecs = -1;
        }
    }

    public final boolean timeUp(long startTimeMillis) {
        return timeLimited() && System.currentTimeMillis() >
                startTimeMillis + maxTimeSecs * 1000;
    }


    public boolean multiThreaded() {
        return !singleThreaded();
    }


    public final TestSettings linkActive(Address from, Address to,
                                         boolean linkActive) {
        this.linkActive
                .put(new ImmutablePair<>(from.rootAddress(), to.rootAddress()),
                        linkActive);
        return this;
    }

    public final TestSettings senderActive(Address from, boolean senderActive) {
        this.senderActive.put(from.rootAddress(), senderActive);
        return this;
    }

    public final TestSettings receiverActive(Address to,
                                             boolean receiverActive) {
        this.receiverActive.put(to.rootAddress(), receiverActive);
        return this;
    }

    public final TestSettings nodeActive(Address node, boolean nodeActive) {
        receiverActive(node, nodeActive);
        senderActive(node, nodeActive);
        return this;
    }

    @SafeVarargs
    public final TestSettings partition(Collection<Address>... partitions) {
        networkActive(false);
        for (Collection<Address> partition : partitions) {
            for (Address from : partition) {
                for (Address to : partition) {
                    if (!from.rootAddress().equals(to.rootAddress())) {
                        linkActive(from.rootAddress(), to.rootAddress(), true);
                    }
                }
            }
        }
        return this;
    }

    public final TestSettings partition(Address... partition) {
        return partition(Arrays.asList(partition));
    }

    /**
     * Clears the active/inactive status of the network, all links, all senders,
     * all receivers. Does <i>not</i> change the deliver rate/reliability of any
     * of the above (in RunSettings).
     */
    public final TestSettings reconnect() {
        networkActive = true;
        linkActive.clear();
        senderActive.clear();
        receiverActive.clear();
        return this;
    }

    public TestSettings resetNetwork() {
        return reconnect();
    }

    /**
     * Computes whether or not the link between the sender and receiver is
     * active by first looking, in order of priority, at the status of the link,
     * the status of the sender, the status of the receiver, and then the status
     * of the network.
     *
     * @param messageEnvelope
     *         the candidate messageEnvelope
     * @return whether the messageEnvelope should be delivered
     */
    public boolean shouldDeliver(MessageEnvelope messageEnvelope) {
        Address from = messageEnvelope.from().rootAddress();
        Address to = messageEnvelope.to().rootAddress();

        if (from.equals(to)) {
            return true;
        }

        Boolean b = linkActive.get(new ImmutablePair<>(from, to));
        if (b != null) {
            return b;
        }
        b = senderActive.get(from);
        if (b != null) {
            return b;
        }
        b = receiverActive.get(to);
        if (b != null) {
            return b;
        }
        return networkActive;
    }

    public TestSettings clear() {
        clearInvariants();
        clearPrunes();
        clearDeliverTimers();
        timeLimited(false);
        singleThreaded(false);
        resetNetwork();
        return this;
    }
}
