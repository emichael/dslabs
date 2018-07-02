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
