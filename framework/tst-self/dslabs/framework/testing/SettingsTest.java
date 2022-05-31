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
import dslabs.framework.testing.runner.RunSettings;
import dslabs.framework.testing.search.SearchSettings;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SettingsTest {
    private static final Address a = new LocalAddress("a"), b =
            new LocalAddress("b"), c = new LocalAddress("c");

    @Test
    public void searchSettingsClone() {
        SearchSettings s = new SearchSettings();
        s.numThreads(5);
        s.outputFreqSecs(42);
        s.addGoal(StatePredicate.CLIENTS_DONE);
        s.addPrune(StatePredicate.RESULTS_OK);
        s.addInvariant(StatePredicate.ALL_RESULTS_SAME);
        s.maxDepth(43);

        // TODO: test more stuff...

        SearchSettings s2 = s.clone();
        assertEquals(s.numThreads(), s2.numThreads());
        assertEquals(s.outputFreqSecs(), s2.outputFreqSecs());
        assertEquals(s.goals().size(), s2.goals().size());
        assertEquals(s.goals().stream().map(StatePredicate::name).sorted()
                      .collect(Collectors.toList()),
                s2.goals().stream().map(StatePredicate::name).sorted()
                  .collect(Collectors.toList()));
        assertEquals(s.prunes().size(), s2.prunes().size());
        assertEquals(s.prunes().stream().map(StatePredicate::name).sorted()
                      .collect(Collectors.toList()),
                s2.prunes().stream().map(StatePredicate::name).sorted()
                  .collect(Collectors.toList()));
        assertEquals(s.invariants().size(), s2.invariants().size());
        assertEquals(s.invariants().stream().map(StatePredicate::name).sorted()
                      .collect(Collectors.toList()),
                s2.invariants().stream().map(StatePredicate::name).sorted()
                  .collect(Collectors.toList()));
    }
}
