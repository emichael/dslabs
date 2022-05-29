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
import dslabs.framework.Timer;
import dslabs.framework.VizIgnore;
import java.io.Serializable;
import java.util.Random;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Stores a timer, its delivery address, its duration, and its creation time.
 * Equality is based on delivery address, timer object, and duration only.
 *
 * Note: this class has a natural ordering that is inconsistent with equals.
 */
@Data
@EqualsAndHashCode(
        of = {"to", "timer", "minTimerLengthMillis", "maxTimerLengthMillis"})
public final class TimerEnvelope
        implements Serializable, Comparable<TimerEnvelope> {
    private static final Random rand = new Random();

    private final Address to;
    private final Timer timer;

    @VizIgnore private final int minTimerLengthMillis, maxTimerLengthMillis,
            timerLengthMillis;

    @VizIgnore private final long startTimeNanos;

    public TimerEnvelope(Address to, Timer timer, int minTimerLengthMillis,
                         int maxTimerLengthMillis) {
        this.to = to;
        this.timer = timer;
        this.minTimerLengthMillis = minTimerLengthMillis;
        this.maxTimerLengthMillis = maxTimerLengthMillis;

        if (minTimerLengthMillis > maxTimerLengthMillis) {
            throw new IllegalArgumentException(
                    "Minimum timer length greater than maximum timer length");
        }

        if (minTimerLengthMillis == maxTimerLengthMillis) {
            this.timerLengthMillis = minTimerLengthMillis;
        } else {
            this.timerLengthMillis = minTimerLengthMillis + rand.nextInt(
                    1 + maxTimerLengthMillis - minTimerLengthMillis);
        }

        this.startTimeNanos = System.nanoTime();
    }

    public long endTimeNanos() {
        return startTimeNanos + (((long) timerLengthMillis()) * 1000000);
    }

    public final long timeRemainingNanos() {
        return endTimeNanos() - System.nanoTime();
    }

    public final boolean isDue() {
        return timeRemainingNanos() <= 0;
    }

    @Override
    public final int compareTo(TimerEnvelope o) {
        if (o == null) {
            return 1;
        }
        return Long.compare(endTimeNanos(), o.endTimeNanos());
    }

    @Override
    public String toString() {
        return String.format("Timer(-> %s, %s)", to, timer);
    }
}
