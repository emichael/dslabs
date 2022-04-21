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

package dslabs.framework.testing.junit;

import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.search.SerializableTrace;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@RequiredArgsConstructor
public class CheckSavedTracesTest extends BaseJUnitTest {
    private static boolean prevSaveTraces;

    @Setter private static String[] traceNames = null;
    @Setter private static String labId = null;
    @Setter private static Integer labPart = null;

    @BeforeClass
    public static void disableTraceSaving() {
        prevSaveTraces = GlobalSettings.saveTraces();
        GlobalSettings.saveTraces(false);
    }

    @AfterClass
    public static void resetTraceSaving() {
        GlobalSettings.saveTraces(prevSaveTraces);
    }

    @Parameters(name = "{0}")
    public static Collection<SerializableTrace[]> traceFiles() {
        if (traceNames != null) {
            return Arrays.stream(traceNames).map(SerializableTrace::loadTrace)
                         .filter(Objects::nonNull)
                         .map(t -> new SerializableTrace[]{t})
                         .collect(Collectors.toUnmodifiableList());
        }

        var s = Arrays.stream(SerializableTrace.traces());
        if (labId != null) {
            s = s.filter(t -> t.labId().equals(labId));
            if (labPart != null) {
                s = s.filter(t -> Objects.equals(t.labPart(), labPart));
            }
        }

        return s.map(t -> new SerializableTrace[]{t})
                .collect(Collectors.toUnmodifiableList());
    }

    private final SerializableTrace trace;

    @Test
    @Category(SearchTests.class)
    public void checkTrace() {
        StringBuilder msg = new StringBuilder();
        msg.append("Replaying trace ");
        msg.append(trace.fileName().toString());
        if (trace.testMethodName() != null) {
            msg.append(" generated from ");
            msg.append(trace.testMethodName());
            if (trace.testClassName() != null) {
                msg.append(" in ");
                msg.append(trace.testClassName());
            }
        }
        msg.append("\n");
        System.out.println(msg);
        searchSettings.outputFreqSecs(-1);
        searchSettings.singleThreaded(true);
        for (StatePredicate invariant : trace.invariants()) {
            searchSettings.addInvariant(invariant);
        }
        traceReplay(trace.initialState(), trace.history());
    }
}
