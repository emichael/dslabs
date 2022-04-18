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

import dslabs.framework.Timer;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.TimerEnvelope;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TimerQueueTest {

    @Data
    private static class T implements Timer {
    }

    private TimerQueue tq = new TimerQueue();

    @Before
    public void setUp() {
        tq = new TimerQueue();
    }

    private static TimerEnvelope te(int n, int timerLengthMillis) {
        return new TimerEnvelope(new LocalAddress(Integer.toString(n)), new T(),
                timerLengthMillis, timerLengthMillis);
    }

    private static TimerEnvelope te(int n, int minTimerLengthMillis,
                                    int maxTimerLengthMillis) {
        return new TimerEnvelope(new LocalAddress(Integer.toString(n)), new T(),
                minTimerLengthMillis, maxTimerLengthMillis);
    }

    private List<TimerEnvelope> deliverable() {
        LinkedList<TimerEnvelope> l = new LinkedList<>();
        for (TimerEnvelope t : tq.deliverable()) {
            l.add(t);
        }
        return l;
    }

    private void assertDeliverable(TimerEnvelope... tes) {
        Collection<TimerEnvelope> d = deliverable();
        for (TimerEnvelope t : tes) {
            assertTrue(tq.isDeliverable(t));
            assertTrue(d.contains(t));
        }
    }

    private void assertNotDeliverable(TimerEnvelope... tes) {
        Collection<TimerEnvelope> d = deliverable();
        for (TimerEnvelope t : tes) {
            assertFalse(tq.isDeliverable(t));
            assertFalse(d.contains(t));
        }
    }

    @Test
    public void equality() {
        assertEquals(te(1, 1), te(1, 1));
        assertEquals(te(1, 1), te(1, 1, 1));

        assertNotEquals(te(2, 1), te(1, 1));
        assertNotEquals(te(1, 1), te(1, 2));

        assertNotEquals(te(1, 1, 1), te(1, 0, 1));
        assertNotEquals(te(1, 1, 1), te(1, 1, 2));
    }

    @Test
    public void notAddedNotDeliverable() {
        assertNotDeliverable(te(1, 1));
    }

    @Test
    public void basicAdd() {
        tq.add(te(1, 1));
        assertDeliverable(te(1, 1));
    }

    @Test
    public void sameLengthNotDeliverable() {
        tq.add(te(1, 1));
        tq.add(te(2, 1));

        assertDeliverable(te(1, 1));
        assertNotDeliverable(te(2, 1));
    }

    @Test
    public void shorterFirstNotDeliverable() {
        tq.add(te(1, 1));
        tq.add(te(2, 2));

        assertDeliverable(te(1, 1));
        assertNotDeliverable(te(2, 1));
    }

    @Test
    public void longerFirstDeliverable() {
        tq.add(te(1, 2));
        tq.add(te(2, 1));

        assertDeliverable(te(1, 2), te(2, 1));
    }

    @Test
    public void addRemoveGet() {
        tq.add(te(1, 1));
        tq.add(te(2, 2));

        assertDeliverable(te(1, 1));
        assertNotDeliverable(te(2, 1));

        tq.remove(te(1, 1));

        assertDeliverable(te(2, 2));
        assertNotDeliverable(te(1, 1));
    }

    @Test
    public void canRemoveNonexistent() {
        tq.remove(te(1, 1));
    }

    @Test
    public void randomTimers() {
        for (int i = 1; i <= 4; i++) {
            for (int j = i; j <= 4; j++) {
                for (int k = 1; k <= 4; k++) {
                    for (int l = k; l <= 4; l++) {
                        setUp();
                        TimerEnvelope te1 = te(1, i, j), te2 = te(2, k, l);
                        tq.add(te1);
                        assertDeliverable(te1);
                        tq.add(te2);
                        assertDeliverable(te1);
                        if (te2.minTimerLengthMillis() <
                                te1.maxTimerLengthMillis()) {
                            assertDeliverable(te2);
                        } else {
                            assertNotDeliverable(te2);
                        }
                    }
                }
            }
        }
    }
}
