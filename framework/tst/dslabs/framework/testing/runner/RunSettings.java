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
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TestSettings;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;


/**
 * Collection of settings used by the run tests.
 *
 * Safe for concurrent access.
 */
public class RunSettings extends TestSettings<RunSettings> {
    private final static Random rand = new Random();

    /* Defaults */
    private static final double DEFAULT_UNRELIABLE_FRACTION_DELIVERED = 0.5;

    /* Settings */
    @Getter @Setter private volatile boolean waitForClients = true;

    // Network settings
    private final Map<Pair<Address, Address>, Double> linkDeliverRate =
            new ConcurrentHashMap<>();
    private final Map<Address, Double> senderDeliverRate =
            new ConcurrentHashMap<>();
    private final Map<Address, Double> receiverDeliverRate =
            new ConcurrentHashMap<>();
    private volatile Double networkDeliverRate = null;

    @Override
    protected final RunSettings self() {
        return this;
    }

    public RunSettings networkDeliverRate(double networkDeliverRate) {
        if (networkDeliverRate < 0.0 || networkDeliverRate > 1.0) {
            throw new IllegalArgumentException();
        }

        this.networkDeliverRate = networkDeliverRate;
        return this;
    }

    public RunSettings networkUnreliable(boolean networkUnreliable) {
        if (networkUnreliable && networkDeliverRate == null) {
            networkDeliverRate = DEFAULT_UNRELIABLE_FRACTION_DELIVERED;
        } else if (!networkUnreliable) {
            networkDeliverRate = null;
        }
        return this;
    }

    public RunSettings linkDeliverRate(Address from, Address to,
                                       double linkDeliverRate) {
        if (linkDeliverRate < 0.0 || linkDeliverRate > 1.0) {
            throw new IllegalArgumentException();
        }

        this.linkDeliverRate.put(
                new ImmutablePair<>(from.rootAddress(), to.rootAddress()),
                linkDeliverRate);
        return this;
    }

    public RunSettings linkUnreliable(Address from, Address to,
                                      boolean linkUnreliable) {
        return mapUnreliable(linkDeliverRate,
                new ImmutablePair<>(from.rootAddress(), to.rootAddress()),
                linkUnreliable);
    }

    public RunSettings senderDeliverRate(Address from,
                                         double senderDeliverRate) {
        if (senderDeliverRate < 0.0 || senderDeliverRate > 1.0) {
            throw new IllegalArgumentException();
        }

        this.senderDeliverRate.put(from.rootAddress(), senderDeliverRate);
        return this;
    }

    public RunSettings senderUnreliable(Address from,
                                        boolean senderUnreliable) {
        return mapUnreliable(senderDeliverRate, from.rootAddress(),
                senderUnreliable);
    }

    public RunSettings receiverDeliverRate(Address to,
                                           double receiverDeliverRate) {
        if (receiverDeliverRate < 0.0 || receiverDeliverRate > 1.0) {
            throw new IllegalArgumentException();
        }

        this.receiverDeliverRate.put(to.rootAddress(), receiverDeliverRate);
        return this;
    }

    public RunSettings receiverUnreliable(Address to,
                                          boolean receiverUnreliable) {
        return mapUnreliable(receiverDeliverRate, to.rootAddress(),
                receiverUnreliable);
    }

    private <T> RunSettings mapUnreliable(Map<T, Double> map, T key,
                                          boolean unreliable) {
        if (unreliable) {
            map.compute(key, (k, v) -> v == null || v > 1.0 ?
                    DEFAULT_UNRELIABLE_FRACTION_DELIVERED : v);
        } else {
            // TODO: clean up using 2.0 as a placeholder
            map.put(key, 2.0);
        }
        return this;
    }

    public RunSettings nodeDeliverRate(Address node, double rate) {
        senderDeliverRate(node, rate);
        receiverDeliverRate(node, rate);
        return this;
    }

    public RunSettings nodeUnreliable(Address node, boolean unreliable) {
        senderUnreliable(node, unreliable);
        receiverUnreliable(node, unreliable);
        return this;
    }

    @Override
    public RunSettings resetNetwork() {
        super.resetNetwork();

        linkDeliverRate.clear();
        senderDeliverRate.clear();
        receiverDeliverRate.clear();
        networkDeliverRate = null;
        return this;
    }

    /**
     * First determines whether the link is active at all. Then computes the
     * reliability of the link for the given messageEnvelope. Then draws a
     * random number and determines whether the messageEnvelope should be sent.
     * The reliability of the link is one of the following, in priority order:
     * link reliability, sender reliability, receiver reliability, global
     * reliability. Not that calling one of the {@code xUnreliable} methods with
     * {@code false} is the same as setting the reliability to 100%.
     *
     * @param messageEnvelope
     *         the candidate messageEnvelope
     * @return whether the messageEnvelope should be delivered
     */
    @Override
    public boolean shouldDeliver(MessageEnvelope messageEnvelope) {
        Address from = messageEnvelope.from().rootAddress();
        Address to = messageEnvelope.to().rootAddress();

        if (from.equals(to)) {
            return true;
        }

        if (!super.shouldDeliver(messageEnvelope)) {
            return false;
        }

        Double deliverRate;
        Pair<Address, Address> link = new ImmutablePair<>(from, to);
        if (linkDeliverRate.containsKey(link)) {
            deliverRate = linkDeliverRate.get(link);
        } else if (senderDeliverRate.containsKey(from)) {
            deliverRate = senderDeliverRate.get(from);
        } else if (receiverDeliverRate.containsKey(to)) {
            // TODO: multiply deliver rates for sender and receiver?
            deliverRate = receiverDeliverRate.get(to);
        } else {
            deliverRate = networkDeliverRate;
        }

        return deliverRate == null || deliverRate > 1.0 ||
                rand.nextDouble() < deliverRate;
    }

    @Override
    public RunSettings clear() {
        super.clear();
        waitForClients(true);
        resetNetwork();
        return this;
    }
}
