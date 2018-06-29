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
import dslabs.framework.Timeout;
import java.io.Serializable;
import java.util.Random;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
@Data
@EqualsAndHashCode(of = {"to", "timeout", "minTimeoutLengthMillis",
        "maxTimeoutLengthMillis"})
public final class TimeoutEnvelope
        implements Serializable, Comparable<TimeoutEnvelope> {
    private static final Random rand = new Random();

    private final Address to;
    private final Timeout timeout;

    private final int minTimeoutLengthMillis, maxTimeoutLengthMillis,
            timeoutLengthMillis;

    private final long startTimeNanos;

    public TimeoutEnvelope(Address to, Timeout timeout,
                           int minTimeoutLengthMillis,
                           int maxTimeoutLengthMillis) {
        this.to = to;
        this.timeout = timeout;
        this.minTimeoutLengthMillis = minTimeoutLengthMillis;
        this.maxTimeoutLengthMillis = maxTimeoutLengthMillis;

        if (minTimeoutLengthMillis > maxTimeoutLengthMillis) {
            throw new IllegalArgumentException(
                    "Minimum timeout length greater than maximum timeout length");
        }

        if (minTimeoutLengthMillis == maxTimeoutLengthMillis) {
            this.timeoutLengthMillis = minTimeoutLengthMillis;
        } else {
            this.timeoutLengthMillis = minTimeoutLengthMillis + rand.nextInt(
                    1 + maxTimeoutLengthMillis - minTimeoutLengthMillis);
        }

        this.startTimeNanos = System.nanoTime();
    }

    public long endTimeNanos() {
        return startTimeNanos + (((long) timeoutLengthMillis()) * 1000000);
    }

    public final long timeRemainingNanos() {
        return endTimeNanos() - System.nanoTime();
    }

    public final boolean isDue() {
        return timeRemainingNanos() <= 0;
    }

    @Override
    public final int compareTo(TimeoutEnvelope o) {
        if (o == null) {
            return 1;
        }
        return Long.compare(endTimeNanos(), o.endTimeNanos());
    }

    @Override
    public String toString() {
        return String.format("Timeout(-> %s, %s)", to, timeout);
    }
}
