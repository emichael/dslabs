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
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.runner.RunSettings;
import dslabs.framework.testing.runner.RunState;
import dslabs.framework.testing.search.Search;
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
import static dslabs.framework.testing.search.SearchResults.EndCondition.GOAL_FOUND;
import static dslabs.framework.testing.search.SearchResults.EndCondition.INVARIANT_VIOLATED;
import static dslabs.framework.testing.search.SearchResults.EndCondition.SPACE_EXHAUSTED;
import static dslabs.framework.testing.search.SearchResults.EndCondition.TIME_EXHAUSTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(DSLabsTestRunner.class)
public abstract class BaseJUnitTest {
    /* Settings */
    protected RunSettings runSettings;
    protected SearchSettings searchSettings;
    private SearchSettings lastSearchSettings;

    /* States */
    protected RunState runState;
    protected SearchState initSearchState;
    private SearchState bfsStartState;

    /* Internal */
    private Set<Thread> startedThreads;
    private boolean failedSearchTest;
    private SearchResults searchResults;
    private Description testDescription;


    protected final boolean isRunTest() {
        return DSLabsTestListener.isInCategory(testDescription, RunTests.class);
    }

    protected final boolean isSearchTest() {
        return DSLabsTestListener
                .isInCategory(testDescription, SearchTests.class);
    }

    protected void setupTest() {
    }

    protected void setupRunTest() {
    }

    protected void setupSearchTest() {
    }

    protected void shutdownTest() throws InterruptedException {
    }

    protected void verifyTest() throws Throwable {
    }

    protected void cleanupTest() {
    }

    @Rule public TestRule rule = new TestRule() {
        @Override
        public Statement apply(final Statement base,
                               final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        testDescription = description;
                        startedThreads = new HashSet<>();
                        failedSearchTest = false;

                        setupTest();
                        if (isRunTest()) {
                            runSettings = new RunSettings();
                            setupRunTest();
                        }
                        if (isSearchTest()) {
                            searchSettings = new SearchSettings();
                            setupSearchTest();
                        }

                        try {
                            base.evaluate();
                        } finally {
                            shutdownTest();
                            shutdownStartedThreads();

                            if (runState != null) {
                                runState.stop();
                            }
                        }

                        verifyTest();
                        if (runState != null) {
                            if (runState.exceptionThrown()) {
                                fail("Exception(s) thrown by running nodes.");
                            }

                            assertRunInvariantsHold();
                        }

                        if (failedSearchTest) {
                            fail("Search test failed.");
                        }
                    } finally {
                        cleanupTest();
                        runSettings = null;
                        searchSettings = null;
                        lastSearchSettings = null;
                        runState = null;
                        initSearchState = null;
                        bfsStartState = null;
                        startedThreads = null;
                        searchResults = null;
                        testDescription = null;

                        // Do garbage collection and take a quick nap to limit cross-test interference
                        System.gc();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            };
        }
    };

    protected void shutdownStartedThreads() throws InterruptedException {
        for (Thread thread : startedThreads) {
            thread.interrupt();
        }

        for (Thread thread : startedThreads) {
            thread.join();
        }
    }


    /* Addresses */

    public static Address client(int i) {
        return new LocalAddress("client" + i);
    }

    public static Address server(int i) {
        return new LocalAddress("server" + i);
    }


    /* Run test helper methods */

    protected final void assertRunInvariantsHold() {
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

    protected final void startThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        startedThreads.add(thread);
        thread.start();
    }

    protected final void sendCommandAndCheck(Client client, Command command,
                                             Result expectedResult)
            throws InterruptedException {
        client.sendCommand(command);
        Result result = client.getResult();
        assertEquals(expectedResult, result);
    }

    protected final void assertMaxWaitTimeLessThan(long allowedMillis) {
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


    /* Search helper methods */

    protected final void bfs(SearchState searchState,
                             SearchSettings searchSettings) {
        assert searchState != null;
        bfsStartState = searchState;
        lastSearchSettings = searchSettings;
        searchResults = Search.bfs(searchState, searchSettings);
        assertEndConditionValid();
    }

    protected final void bfs(SearchState searchState) {
        bfs(searchState, searchSettings);
    }

    protected final void dfs(SearchState searchState,
                             SearchSettings searchSettings) {
        assert searchState != null;
        lastSearchSettings = searchSettings;
        searchResults = Search.dfs(searchState, searchSettings);
        assertEndConditionValid();
    }

    protected final void dfs(SearchState searchState) {
        dfs(searchState, searchSettings);
    }

    @SneakyThrows
    private void assertEndConditionValid() {
        final EndCondition ec = searchResults.endCondition();
        if (!(ec == INVARIANT_VIOLATED || ec == EXCEPTION_THROWN)) {
            return;
        }

        final SearchState terminal;
        final StatePredicate invariant;
        final Throwable exception;
        if (ec == INVARIANT_VIOLATED) {
            terminal = searchResults.invariantViolatingState();
            invariant = searchResults.invariantViolated();
            exception = null;
        } else {
            terminal = searchResults.exceptionalState();
            invariant = null;
            exception = searchResults.exceptionalState().thrownException();
        }

        final SearchState humanReadable =
                SearchState.humanReadableTraceEndState(terminal);
        humanReadable.printTrace();

        if (ec == INVARIANT_VIOLATED) {
            System.err.println(
                    "\n" + invariant.errorMessage(humanReadable) + "\n");
        } else {
            System.err.println();
        }

        if (GlobalSettings.startVisualization()) {
            final SearchSettings settings = lastSearchSettings;
            Thread thread = new Thread(() -> {
                VizClient vc = new VizClient(humanReadable, settings, true);
                try {
                    vc.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "VizClient");
            thread.setDaemon(false);
            thread.start();

            if (ec == INVARIANT_VIOLATED) {
                System.err.println(
                        "Invariant violated. Visualization started.\n");
            } else {
                exception.printStackTrace();
                System.err.println(
                        "\nException thrown by nodes during search. Visualization started.\n");
            }

            throw new VizClientStarted();
        }

        if (ec == INVARIANT_VIOLATED) {
            fail("Invariant violated (see above trace and information).");
        }

        System.err.println(
                "Exception thrown by nodes during search (see above trace).\n");
        throw exception;
    }

    protected final void clearSearchResults() {
        searchResults = null;
    }

    protected final boolean goalFound() {
        assert !searchResults.goalsSought().isEmpty();
        return searchResults.endCondition() == GOAL_FOUND;
    }

    protected final SearchState goalMatchingState() {
        assert !searchResults.goalsSought().isEmpty();
        assertGoalFound(true);
        return searchResults.goalMatchingState();
    }

    protected final void assertGoalFound() {
        assert !searchResults.goalsSought().isEmpty();
        assertGoalFound(false);
    }

    private void assertGoalFound(boolean endTestOnFailure) {
        assert searchResults.goalsSought() != null &&
                !searchResults.goalsSought().isEmpty();

        final EndCondition ec = searchResults.endCondition();
        if (ec == GOAL_FOUND) {
            return;
        }

        assert ec != INVARIANT_VIOLATED && ec != EXCEPTION_THROWN;

        final List<StatePredicate> goals =
                new ArrayList<>(searchResults.goalsSought());
        final StringBuilder sb = new StringBuilder();
        sb.append("Could not find state matching");
        if (goals.size() == 1) {
            sb.append(" \"").append(goals.get(0).name()).append("\"");
        } else {
            sb.append(" one of the following:");
            for (StatePredicate goal : goals) {
                sb.append("\n\t- \"").append(goal).append("\"");
            }
        }
        sb.append(switch (ec) {
            case SPACE_EXHAUSTED -> "\nSearch space was exhausted.";
            case TIME_EXHAUSTED -> "\nSearch ran out of time.";
            default -> "";
        });

        if (GlobalSettings.startVisualization() && bfsStartState != null) {
            final SearchState humanReadable =
                SearchState.humanReadableTraceEndState(bfsStartState);
            final SearchSettings settings = lastSearchSettings;
            Thread thread = new Thread(() -> {
                VizClient vc = new VizClient(humanReadable, settings, true);
                try {
                    vc.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }, "VizClient");
            thread.setDaemon(false);
            thread.start();

            sb.append("\nStarting visualization from beginning of search.\n");

            System.err.println(sb.toString());

            throw new VizClientStarted();
        }

        if (endTestOnFailure) {
            fail(sb.toString());
        } else {
            System.err.println(sb);
            failTestAndContinue();
        }
    }

    protected final void assertSpaceExhausted() {
        assert searchResults.goalsSought() == null ||
                searchResults.goalsSought().isEmpty();

        final EndCondition ec = searchResults.endCondition();
        if (searchResults.endCondition() == SPACE_EXHAUSTED) {
            return;
        }

        assert ec == TIME_EXHAUSTED;

        System.err.println("Could not exhaust search space, ran out of time.");
        failTestAndContinue();
    }

    private void failTestAndContinue() {
        System.err.println(
                "Search test failed. Continuing to run the rest of the test...\n");
        failedSearchTest = true;
    }


    /* Utils */

    protected final long nodesSize() {
        int total = 0;
        for (Node node : runState.nodes()) {
            total += Cloning.size(node);
            // TODO: consider including timers as below
            // for (TimerEnvelope te : runState.timers(node.address())) {
            //     total += Cloning.size(te.timer());
            // }
        }
        return total;
    }

    public static String readableSize(long size) {
        // Modified from: https://stackoverflow.com/a/5599842
        if (size == 0) {
            return "0 B";
        }

        final String prefix;
        if (size < 0) {
            prefix = "-";
        } else {
            prefix = "";
        }

        size = Math.abs(size);

        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        final int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        final String baseStr = new DecimalFormat("#,##0.#")
                .format(size / Math.pow(1024, digitGroups)) + " " +
                units[digitGroups];
        return prefix + baseStr;
    }
}
