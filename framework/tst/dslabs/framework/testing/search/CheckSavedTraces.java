/*
 * Copyright (c) 2021 Ellis Michael (emichael@cs.washington.edu)
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

import dslabs.framework.Address;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.junit.BaseJUnitTest;
import dslabs.framework.testing.junit.SearchTests;
import dslabs.framework.testing.utils.GlobalSettings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static dslabs.framework.testing.search.SerializableTrace.TRACE_DIR_NAME;

@RunWith(Parameterized.class)
public class CheckSavedTraces extends BaseJUnitTest {
    private static boolean prevSaveTraces;

    // TODO: create a way to run this on a single command-line specified file

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
    public static Collection<Object[]> traceFiles() {
        // TODO: move to SerializableTrace, filter for *.trace
        final File traceDir = new File(TRACE_DIR_NAME);
        if (!traceDir.exists()) {
            return Collections.EMPTY_LIST;
        }
        return Arrays.stream(Objects.requireNonNull(traceDir.listFiles()))
                     .map(f -> new File[]{f}).collect(Collectors.toList());
    }

    private final File traceFile;

    public CheckSavedTraces(File traceFile) {
        this.traceFile = traceFile;
    }

    @Test
    @Category(SearchTests.class)
    public void checkTrace() {
        System.out.println("Replaying trace " + traceFile);

        SerializableTrace trace;
        try (ObjectInputStream is = new ObjectInputStream(
                new FileInputStream(traceFile))) {
            trace = (SerializableTrace) is.readObject();
        } catch (ClassNotFoundException | IOException e) {
            System.err.println(
                    "Trace no longer loads; event definitions may have changed");
            return;
        }

        initSearchState = new SearchState(trace.stateGenerator());
        for (Address a : trace.servers()) {
            initSearchState.addServer(a);
        }

        for (Pair<Address, Workload> p : trace.clientWorkers()) {
            initSearchState.addClientWorker(p.getLeft(), p.getRight());
        }

        for (StatePredicate p : trace.invariants()) {
            searchSettings.addInvariant(p);
        }

        searchSettings.outputFreqSecs(-1);
        searchSettings.singleThreaded(true);

        traceReplay(initSearchState, trace.history());

        System.out.println(
                "Trace no longer causes error or no longer fully replays.\n");
    }
}
