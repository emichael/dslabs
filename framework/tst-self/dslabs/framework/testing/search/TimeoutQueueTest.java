package dslabs.framework.testing.search;

import dslabs.framework.Timeout;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.TimeoutEnvelope;
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

public class TimeoutQueueTest {

    @Data
    private static class T implements Timeout {
    }

    private TimeoutQueue tq = new TimeoutQueue();

    @Before
    public void setUp() {
        tq = new TimeoutQueue();
    }

    private static TimeoutEnvelope te(int n, int timeoutLengthMillis) {
        return new TimeoutEnvelope(new LocalAddress(Integer.toString(n)),
                new T(), timeoutLengthMillis, timeoutLengthMillis);
    }

    private static TimeoutEnvelope te(int n, int minTimeoutLengthMillis,
                                      int maxTimeoutLengthMillis) {
        return new TimeoutEnvelope(new LocalAddress(Integer.toString(n)),
                new T(), minTimeoutLengthMillis, maxTimeoutLengthMillis);
    }

    private List<TimeoutEnvelope> deliverable() {
        LinkedList<TimeoutEnvelope> l = new LinkedList<>();
        for (TimeoutEnvelope t : tq.deliverable()) {
            l.add(t);
        }
        return l;
    }

    private void assertDeliverable(TimeoutEnvelope... tes) {
        Collection<TimeoutEnvelope> d = deliverable();
        for (TimeoutEnvelope t : tes) {
            assertTrue(tq.isDeliverable(t));
            assertTrue(d.contains(t));
        }
    }

    private void assertNotDeliverable(TimeoutEnvelope... tes) {
        Collection<TimeoutEnvelope> d = deliverable();
        for (TimeoutEnvelope t : tes) {
            assertFalse(tq.isDeliverable(t));
            assertFalse(d.contains(t));
        }
    }

    @Test
    public void testEquals() {
        assertEquals(te(1, 1), te(1, 1));
        assertEquals(te(1, 1), te(1, 1, 1));

        assertNotEquals(te(2, 1), te(1, 1));
        assertNotEquals(te(1, 1), te(1, 2));

        assertNotEquals(te(1, 1, 1), te(1, 0, 1));
        assertNotEquals(te(1, 1, 1), te(1, 1, 2));
    }

    @Test
    public void testNotAddedNotDeliverable() {
        assertNotDeliverable(te(1, 1));
    }

    @Test
    public void testBasicAdd() {
        tq.add(te(1, 1));
        assertDeliverable(te(1, 1));
    }

    @Test
    public void testSameLengthNotDeliverable() {
        tq.add(te(1, 1));
        tq.add(te(2, 1));

        assertDeliverable(te(1, 1));
        assertNotDeliverable(te(2, 1));
    }

    @Test
    public void testShorterFirstNotDeliverable() {
        tq.add(te(1, 1));
        tq.add(te(2, 2));

        assertDeliverable(te(1, 1));
        assertNotDeliverable(te(2, 1));
    }

    @Test
    public void testLongerFirstDeliverable() {
        tq.add(te(1, 2));
        tq.add(te(2, 1));

        assertDeliverable(te(1, 2), te(2, 1));
    }

    @Test
    public void testAddRemoveGet() {
        tq.add(te(1, 1));
        tq.add(te(2, 2));

        assertDeliverable(te(1, 1));
        assertNotDeliverable(te(2, 1));

        tq.remove(te(1, 1));

        assertDeliverable(te(2, 2));
        assertNotDeliverable(te(1, 1));
    }

    @Test
    public void testCanRemoveNonexistent() {
        tq.remove(te(1, 1));
    }

    @Test
    public void testRandomTimeouts() {
        for (int i = 1; i <= 4; i++) {
            for (int j = i; j <= 4; j++) {
                for (int k = 1; k <= 4; k++) {
                    for (int l = k; l <= 4; l++) {
                        setUp();
                        TimeoutEnvelope te1 = te(1, i, j), te2 = te(2, k, l);
                        tq.add(te1);
                        assertDeliverable(te1);
                        tq.add(te2);
                        assertDeliverable(te1);
                        if (te2.minTimeoutLengthMillis() <
                                te1.maxTimeoutLengthMillis()) {
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