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
import dslabs.framework.testing.ClientWorker;
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
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import static dslabs.framework.testing.search.SearchResults.EndCondition.EXCEPTION_THROWN;
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

    private void baseSetupTest() {
        runSettings = new RunSettings();
        searchSettings = new SearchSettings();
        startedThreads = new HashSet<>();
        failedSearchTest = false;
    }

    protected void setupTest() {
    }

    private void baseShutdownTest() throws InterruptedException {
        shutdownStartedThreads();

        if (runState != null) {
            runState.stop();
        }
    }

    protected void shutdownTest() throws InterruptedException {
    }

    private void baseVerifyTest() throws Throwable {
        if (runState != null) {
            if (runState.exceptionThrown()) {
                fail("Exception(s) thrown by running nodes.");
            }

            assertRunInvariantsHold();
        }

        assertSearchTestsPassed();
    }

    protected void verifyTest() throws Throwable {
    }

    private void baseCleanupTest() {
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

    protected void cleanupTest() {
    }

    @Rule public TestRule rule = new TestRule() {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        baseSetupTest();
                        setupTest();
                        try {
                            base.evaluate();
                        } finally {
                            shutdownTest();
                            baseShutdownTest();
                        }
                        verifyTest();
                        baseVerifyTest();
                    } finally {
                        cleanupTest();
                        baseCleanupTest();
                    }
                }
            };
        }
    };

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
        } else if (searchResults.endCondition().equals(EXCEPTION_THROWN)) {
            exceptionThrown(searchResults);
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

    protected void assertEndConditionValid(SearchResults searchResults) {
        assertEndConditionValid(searchResults, true);
    }

    protected void assertEndConditionValidAndContinue(
            SearchResults searchResults) {
        assertEndConditionValid(searchResults, false);
    }

    private void assertEndConditionValid(SearchResults searchResults,
                                         boolean endTestOnFailure) {
        if (searchResults.endCondition().equals(INVARIANT_VIOLATED)) {
            invariantViolated(searchResults);
        } else if (searchResults.endCondition().equals(EXCEPTION_THROWN)) {
            exceptionThrown(searchResults);
        } else if (endTestOnFailure) {
            assertSearchTestsPassed();
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

        StatePredicate invariant = searchResults.invariantViolated();
        assert invariant != null;

        System.err.println("\n" + invariant.errorMessage(humanReadable) + "\n");

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

            System.err.println("Invariant violated. Visualization started.\n");
            throw new VizClientStarted();
        } else {
            fail("Invariant violated (see above trace and information).");
        }
    }

    @SneakyThrows
    private void exceptionThrown(SearchResults searchResults) {
        Throwable exception =
                searchResults.exceptionalState().thrownException();
        assert exception != null;

        final SearchState humanReadable = SearchState
                .humanReadableTraceEndState(searchResults.exceptionalState());
        humanReadable.printTrace();
        System.err.println();

        if (GlobalSettings.startVisualization()) {
            Thread thread = new Thread(() -> {
                VizClient vc = new VizClient(humanReadable, null, true);
                try {
                    vc.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "VizClient");
            thread.setDaemon(false);
            thread.start();

            exception.printStackTrace();

            System.err.println(
                    "\nException thrown by nodes during search. Visualization started.\n");

            throw new VizClientStarted();
        } else {
            System.err.println(
                    "Exception thrown by nodes during search (see above trace).\n");
            throw exception;
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

        long maxWaitTimeMillis = 0;
        for (ClientWorker cw : runState.clientWorkers()) {
            long t = cw.maxWaitTimeMilis();
            if (t > allowedMillis) {
                fail(String.format("%s waited too long, %s ms (%s ms allowed)",
                        cw.address(), t, allowedMillis));
            }
            maxWaitTimeMillis = Math.max(maxWaitTimeMillis, t);
        }

        System.out.println(
                String.format("Maximum client wait time %s ms (%s ms allowed)",
                        maxWaitTimeMillis, allowedMillis));
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
