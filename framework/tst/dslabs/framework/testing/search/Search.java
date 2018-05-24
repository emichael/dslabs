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

import dslabs.framework.testing.utils.CheckLogger;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NonNull;

import static dslabs.framework.testing.search.SearchResults.EndCondition.INVARIANT_VIOLATED;
import static dslabs.framework.testing.search.SearchResults.EndCondition.SPACE_EXHAUSTED;
import static dslabs.framework.testing.search.SearchResults.EndCondition.TIME_EXHAUSTED;


public abstract class Search {
    private static class BFS {
        // Settings
        private final SearchSettings settings;

        // Shared state
        private final Queue<SearchState> queue = new ConcurrentLinkedQueue<>();
        private final Set<SearchState> discovered =
                Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final AtomicInteger activeWorkers = new AtomicInteger();

        // Resulting state
        private final SearchResults results = new SearchResults();

        // Executor thread state
        private int explored;
        private int depth;
        private long startTimeMillis;
        private long lastLoggedMillis;


        private BFS(SearchSettings settings) {
            this.settings = settings;

            results.invariantsTested(new LinkedList<>(settings.invariants()));
        }

        private boolean searchFinished() {
            boolean workersFinished =
                    settings.singleThreaded() || activeWorkers.get() == 0;

            boolean maxDepthReached = !queue.isEmpty() &&
                    queue.peek().depth() >= settings.maxDepth() &&
                    settings.depthLimited();

            boolean maxTimeReached = settings.timeLimited() &&
                    System.currentTimeMillis() - startTimeMillis >
                            settings.maxTimeSecs() * 1000;

            boolean invariantViolated =
                    results.invariantViolatingState() != null;

            return (workersFinished && queue.isEmpty()) || maxDepthReached ||
                    maxTimeReached || invariantViolated;
        }

        private void printStatus() {
            double time =
                    (System.currentTimeMillis() - startTimeMillis) / 1000.0;
            if (time == 0.0) {
                time += .01;
            }
            System.out.println(String.format(
                    "\tExplored: %s, Depth exploring: %s (%.2fs, %.2fK states/s)",
                    explored, depth, time, explored / time / 1000.0));
        }

        SearchResults run(SearchState initialState) {
            // Executor thread state
            explored = 0;
            depth = initialState.depth();
            startTimeMillis = System.currentTimeMillis();
            lastLoggedMillis = 0;

            // Begin search
            queue.add(initialState);
            discovered.add(initialState);

            ExecutorService executor = null;
            if (settings.multiThreaded()) {
                // TODO: use unbounded queue??
                executor = Executors.newFixedThreadPool(settings.numThreads());
            }

            if (settings.shouldOutputStatus()) {
                System.out.println("Starting breadth-first search...");
            }

            while (!searchFinished()) {
                // If the queue is empty, let's wait on the active workers and retry
                if (queue.isEmpty()) {
                    // Should never be true for singleThreaded execution
                    synchronized (activeWorkers) {
                        if (activeWorkers.get() == 0) {
                            continue;
                        }
                        try {
                            if (settings.timeLimited()) {
                                long waitTime = System.currentTimeMillis() -
                                        (startTimeMillis + (1000 *
                                                settings.maxTimeSecs()));
                                if (waitTime > 0) {
                                    activeWorkers.wait(waitTime);
                                }
                            } else {
                                activeWorkers.wait();
                            }
                            continue;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }

                // Next, let's print out the status if necessary
                if (settings.shouldOutputStatus() &&
                        System.currentTimeMillis() - lastLoggedMillis >
                                settings.outputFreqSecs() * 1000) {
                    lastLoggedMillis = System.currentTimeMillis();
                    printStatus();
                }

                // Finally, let's pop a state off the queue and deal with it
                SearchState next = queue.poll();
                if (next.depth() > depth) {
                    depth = next.depth();
                }
                explored++;
                if (settings.multiThreaded()) {
                    activeWorkers.incrementAndGet();
                    executor.execute(() -> exploreNode(next));
                } else {
                    exploreNode(next);
                }
            }

            if (settings.shouldOutputStatus()) {
                printStatus();
                System.out.println("Search finished.\n");
            }

            if (settings.multiThreaded()) {
                executor.shutdownNow();
                try {
                    // TODO: time how long shutdown takes
                    executor.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                if (!executor.isTerminated()) {
                    throw new RuntimeException("Couldn't shut down search.");
                }
            }

            if (results.invariantViolatingState() != null) {
                results.endCondition(INVARIANT_VIOLATED);
            } else if (queue.isEmpty()) {
                // TODO: queue should always be empty???
                results.endCondition(SPACE_EXHAUSTED);
            } else {
                results.endCondition(TIME_EXHAUSTED);
            }

            return results;
        }

        private void exploreNode(SearchState node) {
            for (Transition transition : node.transitions(settings)) {
                SearchState successor =
                        node.stepTransition(transition, settings, true);

                // If node is null or has already been explored, continue
                if (successor == null || !discovered.add(successor)) {
                    continue;
                }

                // Check all invariants
                if (settings.invariantViolated(successor)) {
                    results.invariantViolated(successor,
                            settings.whichInvariantViolated(successor));
                    break;
                }

                // Run checks on node
                if (GlobalSettings.doChecks()) {
                    // Check if transition is deterministic
                    if (!Objects.equals(successor,
                            node.stepTransition(transition, settings, true))) {
                        CheckLogger.notDeterministic(transition, node);
                    }

                    // Check if transition is idempotent
                    if (transition.isMessage() && !Objects.equals(successor,
                            successor.stepTransition(transition, settings,
                                    true))) {
                        CheckLogger.notIdempotent(transition, node);
                    }
                }

                // Prune away the state if possible
                if (settings.shouldPrune(successor)) {
                    continue;
                }

                queue.add(successor);
            }

            // Done with searching successors, report back to main thread
            if (settings.multiThreaded()) {
                synchronized (activeWorkers) {
                    activeWorkers.decrementAndGet();
                    activeWorkers.notify();
                }
            }
        }
    }


    /**
     * Main model checking loop. Does a breadth-first search starting at
     * initialState, goes until it starts exploring past maxDepth or an explored
     * state violates an invariant.
     *
     * TODO: fix docs
     *
     * @param initialState
     *         the initial state to start at
     * @return a state that violates an invariant, if one exists; otherwise null
     */
    public static SearchResults bfs(@NonNull SearchState initialState,
                                    SearchSettings settings) {
        if (settings == null) {
            settings = new SearchSettings();
        }
        return new BFS(settings).run(initialState);
    }
}
