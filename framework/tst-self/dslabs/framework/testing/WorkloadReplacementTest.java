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
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WorkloadReplacementTest {
    private Address a(String s) {
        return new LocalAddress(s);
    }

    private Pair<String, String> assertReplacements(String command,
                                                    String result,
                                                    String address, int i,
                                                    String newCommand,
                                                    String newResult) {
        Pair<String, String> replaced =
                Workload.doReplacements(command, result, a(address), i);
        assertEquals(newCommand, replaced.getLeft());
        assertEquals(newResult, replaced.getRight());

        Pair<String, String> replacedSame =
                Workload.doReplacements(command, command, a(address), i);
        assertEquals(replacedSame.getLeft(), replacedSame.getRight());

        if (result != null) {
            assertReplacements(command, address, i, newCommand);
        }

        return replaced;
    }

    private String assertReplacements(String command, String address, int i,
                                      String newCommand) {
        assertReplacements(command, null, address, i, newCommand, null);
        return Workload.doReplacements(command, null, a(address), i).getLeft();
    }

    private void runRepeatedly(Runnable r) {
        for (int __ = 0; __ < 1000; __++) {
            r.run();
        }
    }


    @Test
    public void doReplacements() {
        assertReplacements("foo", "bar", "baz", 0, "foo", "bar");
        assertReplacements(null, "foo", "bar", 0, null, null);

        assertReplacements("foo%a", "bar%a", "baz", 0, "foobaz", "barbaz");
        assertReplacements("foo%%a", "bar%%a", "baz", 0, "foo%baz", "bar%baz");
        assertReplacements("foo%a%a%a", "bar%a%a%a", "baz", 0, "foobazbazbaz",
                "barbazbazbaz");
        assertReplacements("a", "a", "baz", 0, "a", "a");

        assertReplacements("foo%i", "bar%i", "baz", 15, "foo15", "bar15");
        assertReplacements("foo%i", "bar%i", "baz", -15, "foo-15", "bar-15");
        assertReplacements("foo%%i", "bar%%i", "baz", 15, "foo%15", "bar%15");
        assertReplacements("foo%i%i%i", "bar%i%i%i", "baz", 15, "foo151515",
                "bar151515");
        assertReplacements("i", "i", "baz", 15, "i", "i");

        assertReplacements("foo%i+1", "bar%i-1", "baz", 15, "foo16", "bar14");
        assertReplacements("foo%i/+1", "bar%i+-1", "baz", 15, "foo15/+1",
                "bar15+-1");

        runRepeatedly(() -> {
            Pair<String, String> r;

            assertReplacements("foo%n1z", "bar%n1z", "baz", 15, "foo1z",
                    "bar1z");

            r = Workload.doReplacements("foo%n5", "foo%n5", a("baz"), 15);
            assertEquals(r.getLeft(), r.getRight());

            r = Workload.doReplacements("foo%n100", "bar%n100", a("baz"), 15);
            assertNotEquals(r.getLeft(), r.getRight());

            r = Workload.doReplacements("%n5", null, a("baz"), 15);
            int i = Integer.parseInt(r.getLeft());
            assertTrue(i >= 1 && i <= 5);

            r = Workload.doReplacements("%n", null, a("baz"), 15);
            i = Integer.parseInt(r.getLeft());
            assertTrue(i >= 1 && i <= 100);

        });

        runRepeatedly(() -> {
            Pair<String, String> r;

            r = Workload.doReplacements("foo%r", "foo%r", a("baz"), 15);
            assertEquals(r.getLeft(), r.getRight());
            assertEquals(11, r.getLeft().length());

            r = Workload.doReplacements("foo%r100", "bar%r100", a("baz"), 15);
            assertNotEquals(r.getLeft(), r.getRight());
            assertEquals(103, r.getLeft().length());

            r = Workload.doReplacements("%r100", "%r101", a("baz"), 15);
            assertNotEquals(r.getLeft(), r.getRight());
        });

        runRepeatedly(() -> {
            Pair<String, String> r;

            r = Workload.doReplacements("%r%n9%i%i+1%i-1%a",
                    "%r%n9%i%i+1%i-1%a", a("baz"), 15);
            assertEquals(r.getLeft(), r.getRight());
            assertEquals(18, r.getLeft().length());
            assertEquals("151614baz", r.getLeft().substring(9));

            r = Workload.doReplacements("%i%r%n9", "%r%n9%i", a("baz"), 15);
            assertEquals(r.getLeft().substring(2),
                    r.getRight().substring(0, 9));
        });
    }
}
