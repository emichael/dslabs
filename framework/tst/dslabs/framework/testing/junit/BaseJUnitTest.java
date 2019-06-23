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

package dslabs.framework.testing.junit;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Node;
import dslabs.framework.Result;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.runner.RunSettings;
import dslabs.framework.testing.runner.RunState;
import dslabs.framework.testing.search.SearchResults;
import dslabs.framework.testing.search.SearchResults.EndCondition;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.utils.Cloning;
import dslabs.framework.testing.utils.GlobalSettings;
import dslabs.framework.testing.visualization.VizClient;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import static dslabs.framework.testing.search.SearchResults.EndCondition.INVARIANT_VIOLATED;
import static dslabs.framework.testing.search.SearchResults.EndCondition.SPACE_EXHAUSTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(DSLabsTestRunner.class)
public abstract class BaseJUnitTest {
    /* Settings */
    protected RunSettings runSettings;
    protected SearchSettings searchSettings;

    /* States */
    protected StateGeneratorBuilder builder;
    protected RunState runState;
    protected SearchState initSearchState;

    protected Set<Thread> startedThreads;

    /* Internal */
    private boolean failedSearchTest;

    @Before
    public void setupTest() {
        runSettings = new RunSettings();
        searchSettings = new SearchSettings();
        startedThreads = new HashSet<>();
        failedSearchTest = false;
    }

    @After
    public void cleanupTest() throws InterruptedException {
        shutdownStartedThreads();

        // TODO: timeout and log error?

        if (runState != null) {
            runState.stop();
        }

        runSettings = null;
        searchSettings = null;
        builder = null;
        runState = null;
        initSearchState = null;
        startedThreads = null;

        // Do garbage collection and take a quick nap to limit cross-test interference
        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void shutdownStartedThreads() throws InterruptedException {
        for (Thread thread : startedThreads) {
            thread.interrupt();
        }

        for (Thread thread : startedThreads) {
            thread.join();
        }
    }

    /* Addresses */
    protected static Address client(int i) {
        return new LocalAddress("client" + i);
    }

    protected static Address server(int i) {
        return new LocalAddress("server" + i);
    }

    /* Assertions */
    protected void assertRunInvariantsHold() {
        if (runSettings.invariantsHold(runState)) {
            return;
        }

        StatePredicate invariant = runSettings.whichInvariantViolated(runState);
        if (invariant == null) {
            // TODO: log the error
            fail("Invariant violated.");
        } else {
            fail(invariant.errorMessage(runState));
        }
    }

    protected void assertEndCondition(EndCondition expectedEndCondition,
                                      SearchResults searchResults) {
        assertEndCondition(expectedEndCondition, searchResults, true);
    }

    protected void assertEndConditionAndContinue(
            EndCondition expectedEndCondition, SearchResults searchResults) {
        assertEndCondition(expectedEndCondition, searchResults, false);
    }

    private void assertEndCondition(EndCondition expectedEndCondition,
                                    SearchResults searchResults,
                                    boolean endTestOnFailure) {
        if (expectedEndCondition.equals(searchResults.endCondition())) {
            if (endTestOnFailure) {
                assertSearchTestsPassed();
            }

            return;
        }

        if (searchResults.endCondition().equals(INVARIANT_VIOLATED)) {
            invariantViolated(searchResults);
        } else if (expectedEndCondition.equals(INVARIANT_VIOLATED)) {
            List<StatePredicate> invariants =
                    new ArrayList<>(searchResults.invariantsTested());

            StringBuilder sb =
                    new StringBuilder("Could not find state matching");
            if (invariants.size() == 1) {
                sb.append(" \"").append(invariants.get(0).negate().name())
                  .append("\"");
            } else {
                sb.append(" one of the following:");
                for (StatePredicate inv : invariants) {
                    sb.append("\n\t- \"").append(inv.negate()).append("\"");
                }
            }

            otherSearchFailure(sb.toString(), endTestOnFailure);
        } else if (expectedEndCondition.equals(SPACE_EXHAUSTED)) {
            otherSearchFailure(
                    "Could not exhaust search space, ran out of time.",
                    endTestOnFailure);
        } else {
            otherSearchFailure(
                    "Exhausted search space, should have run out of time.",
                    endTestOnFailure);
        }
    }

    protected void assertNotEndCondition(EndCondition unexpectedEndCondition,
                                         SearchResults searchResults) {
        assertNotEndCondition(unexpectedEndCondition, searchResults, true);
    }

    protected void assertNotEndConditionAndContinue(
            EndCondition unexpectedEndCondition, SearchResults searchResults) {
        assertNotEndCondition(unexpectedEndCondition, searchResults, false);
    }

    private void assertNotEndCondition(EndCondition unexpectedEndCondition,
                                       SearchResults searchResults,
                                       boolean endTestOnFailure) {
        if (!unexpectedEndCondition.equals(searchResults.endCondition())) {
            if (endTestOnFailure) {
                assertSearchTestsPassed();
            }

            return;
        }

        if (unexpectedEndCondition.equals(INVARIANT_VIOLATED)) {
            invariantViolated(searchResults);
        } else if (unexpectedEndCondition.equals(SPACE_EXHAUSTED)) {
            otherSearchFailure("Ran out of time.", endTestOnFailure);
        } else {
            otherSearchFailure("Exhausted search space.", endTestOnFailure);
        }
    }

    private void otherSearchFailure(String message, boolean endTestOnFailure) {
        if (endTestOnFailure) {
            fail(message);
        } else {
            failedSearchTest = true;
            System.err.println("Search test failed. " + message);
            System.err.println("Continuing to run the rest of the test...\n");
        }
    }

    protected void assertSearchTestsPassed() {
        if (failedSearchTest) {
            fail("Search test failed.");
        }
    }

    private void invariantViolated(SearchResults searchResults) {
        final SearchState humanReadable = SearchState
                .humanReadableTraceEndState(
                        searchResults.invariantViolatingState());

        humanReadable.printTrace();

        StringBuilder sb = new StringBuilder();
        StatePredicate invariant = searchResults.invariantViolated();
        if (invariant == null) {
            // TODO: log the error
            sb.append("Invariant violated.");
        } else {
            sb.append(searchResults.invariantViolated()
                                   .errorMessage(humanReadable));
        }
        sb.append("\nSee above trace.");

        if (GlobalSettings.startVisualization()) {
            Thread thread = new Thread(() -> {
                VizClient vc = new VizClient(humanReadable, invariant, true);
                try {
                    vc.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "VizClient");
            thread.setDaemon(false);
            thread.start();

            sb.append("\nVisualization started.");
            throw new InvariantViolationError(sb.toString());
        } else {
            throw new InvariantViolationError(sb.toString());
        }
    }

    protected void sendCommandAndCheck(Client client, Command command,
                                       Result expectedResult)
            throws InterruptedException {
        client.sendCommand(command);
        Result result = client.getResult();
        assertEquals(expectedResult, result);
    }

    protected void assertMaxWaitTimeLessThan(long allowedMillis) {
        // TODO: maybe shut the runstate and threads down here

        long maxWaitTimeMillis = runState.maxWaitTimeMillis();

        if (maxWaitTimeMillis > allowedMillis) {
            fail(String.format("Client waited too long, %s ms (%s ms allowed)",
                    maxWaitTimeMillis, allowedMillis));
        } else {
            System.out.println(String.format(
                    "Maximum client wait time %s ms (%s ms allowed)",
                    maxWaitTimeMillis, allowedMillis));
        }
    }

    /* Utils */
    protected long nodesSize() {
        // TODO: include timer queues?
        int total = 0;
        for (Node node : runState.nodes()) {
            total += Cloning.size(node);
        }
        return total;
    }


    public static String readableSize(long size) {
        // Taken from: https://stackoverflow.com/a/5599842
        if (size <= 0) {
            return "0";
        }
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#")
                .format(size / Math.pow(1024, digitGroups)) + " " +
                units[digitGroups];
    }
}
