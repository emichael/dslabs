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

import dslabs.framework.testing.Event;
import dslabs.framework.testing.StatePredicate.PredicateResult;
import dslabs.framework.testing.search.SearchState.SearchEquivalenceWrappedSearchState;
import dslabs.framework.testing.utils.CheckLogger;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.NonNull;

import static dslabs.framework.testing.search.SearchResults.EndCondition.EXCEPTION_THROWN;
import static dslabs.framework.testing.search.SearchResults.EndCondition.GOAL_FOUND;
import static dslabs.framework.testing.search.SearchResults.EndCondition.INVARIANT_VIOLATED;
import static dslabs.framework.testing.search.SearchResults.EndCondition.SPACE_EXHAUSTED;
import static dslabs.framework.testing.search.SearchResults.EndCondition.TIME_EXHAUSTED;


/**
 * The base class other search strategies are built off of. Based on the search
 * settings, either executes the search strategy in single-threaded or
 * multi-threaded mode.
 *
 * This search class represents a single instance of a search.
 * {@link #run(SearchState)} should not be called more than once on the same
 * object.
 *
 * Ordinarily, tests should only use the static convenience methods on this
 * class.
 */
public abstract class Search {
    protected final SearchSettings settings;

    private final Lock lock = new ReentrantLock();
    private final Condition searchFinished = lock.newCondition(),
            workerFinished = lock.newCondition();
    /**
     * Protected by lock.
     */
    private int numActiveWorkers = 0;

    private final SearchResults results = new SearchResults();

    private long startTimeMillis;

    protected Search(SearchSettings settings) {
        this.settings = settings;
        results.invariantsTested(new LinkedList<>(settings.invariants()));
        results.goalsSought(new LinkedList<>(settings.goals()));
    }

    /**
     * Should return a adjective describing "search." Only called by the main
     * thread.
     *
     * @return the type of search being executed
     */
    protected abstract String searchType();

    /**
     * Initialize the search, setup any strategy-specific fields. Only called by
     * the main thread.
     *
     * @param initialState
     *         the state the search is initialized with
     */
    protected abstract void initSearch(SearchState initialState);

    /**
     * Should return the status message to display to the user. Should not
     * include leading spaces. Should be thread-safe.
     *
     * @param elapsedSecs
     *         the time elapsed since the start of the search
     * @return the status message
     */
    protected abstract String status(double elapsedSecs);

    /**
     * Determine whether or not the space has been fully explored, up to the
     * limits imposed by the search settings. For example, if the depth-limit
     * has been reached in a breadth-first search, then this method should
     * return {@code true}. This method is free to assume that there are no
     * active workers running for purposes of determining whether the space is
     * exhausted or not. Need not be thread-safe.
     *
     * @return whether the space has been fully explored
     */
    protected abstract boolean spaceExhausted();

    /**
     * Get an executable for the worker thread (or the main thread in
     * single-threaded mode) to run, or {@code null} if there are no workers
     * waiting to run. If {@link #spaceExhausted()} returns {@code false} (and
     * there are no workers started in the interim), should not return
     * {@code null}. Need not be thread-safe.
     *
     * @return the next worker
     */
    protected abstract Runnable getWorker();

    private boolean searchFinished() {
        lock.lock();
        try {
            return ((numActiveWorkers == 0) && spaceExhausted()) ||
                    (settings.timeLimited() &&
                            ((System.currentTimeMillis() - startTimeMillis) >
                                    (settings.maxTimeSecs() * 1000))) ||
                    (results.invariantViolated() != null) ||
                    (results.exceptionThrown()) ||
                    (results.goalMatched() != null);
        } finally {
            lock.unlock();
        }
    }

    private void printStatus() {
        double time = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
        if (time == 0.0) {
            time += .01;
        }
        System.out.println("\t" + status(time));
    }

    protected enum StateStatus {
        VALID, TERMINAL, PRUNED
    }

    /**
     * Convenience method to be used by workers to execute checks for each state
     * encountered.
     *
     * @param s
     *         the state to check
     * @param shouldMinimize
     *         whether or not traces should be run through the minimizer
     */
    protected final StateStatus checkState(SearchState s,
                                           boolean shouldMinimize) {
        if (s.thrownException() != null) {
            if (shouldMinimize) {
                // Log the exception to shut the other threads down
                results.exceptionThrown(null);

                // Minimize the trace and log the actual exception-causing state
                s = TraceMinimizer.minimizeExceptionCausingTrace(s);
            }
            results.exceptionThrown(s);
            return StateStatus.TERMINAL;
        }

        PredicateResult r = settings.invariantViolated(s);
        if (r != null) {
            if (shouldMinimize) {
                // Log the violation to shut the other threads down
                results.invariantViolated(null, r);

                // Minimize the trace and log the actual invariant-violating state
                s = TraceMinimizer.minimizeTrace(s, r);
            }
            results.invariantViolated(s, r);
            return StateStatus.TERMINAL;
        }

        r = settings.goalMatched(s);
        if (r != null) {
            if (shouldMinimize) {
                // Log the goal to shut the other threads down
                results.goalFound(null, r);

                // Minimize the trace and log the actual goal-matching state
                s = TraceMinimizer.minimizeTrace(s, r);
            }
            results.goalFound(s, r);
            return StateStatus.TERMINAL;
        }

        if (GlobalSettings.doErrorChecks()) {
            SearchState previous = s.previous();
            Event e = s.previousEvent();

            if (previous != null) {
                assert e != null;
                // Check if event is deterministic
                if (!Objects.equals(s, previous.stepEvent(e, settings, true))) {
                    CheckLogger.notDeterministic(e, previous);
                }

                // Non-idempotence isn't necessarily an error
                if (GlobalSettings.doAllChecks()) {
                    // Check if event is idempotent
                    if (e.isMessage() && !Objects.equals(s,
                            s.stepEvent(e, settings, true))) {
                        CheckLogger.notIdempotent(e, previous);
                    }
                }
            }
        }

        if (settings.shouldPrune(s)) {
            return StateStatus.PRUNED;
        }

        if (settings.depthLimited() && s.depth() >= settings.maxDepth()) {
            return StateStatus.PRUNED;
        }

        return StateStatus.VALID;
    }

    protected SearchResults run(SearchState initialState) {
        startTimeMillis = System.currentTimeMillis();
        initSearch(initialState);

        if (settings.shouldOutputStatus()) {
            System.out.printf("Starting %s search...%n", searchType());
        }

        if (settings.multiThreaded()) {
            Collection<Thread> workerThreads = new LinkedList<>();

            // Start all of the worker threads
            for (int i = 0; i < settings.numThreads(); i++) {
                Thread t = new Thread(() -> {
                    while (!Thread.interrupted()) {
                        Runnable worker;
                        lock.lock();
                        try {
                            while ((worker = getWorker()) == null) {
                                workerFinished.await();
                            }
                            numActiveWorkers++;
                        } catch (InterruptedException e) {
                            return;
                        } finally {
                            lock.unlock();
                        }

                        worker.run();

                        lock.lock();
                        try {
                            numActiveWorkers--;
                            workerFinished.signal();
                            if (searchFinished()) {
                                searchFinished.signal();
                            }
                        } finally {
                            lock.unlock();
                        }
                    }
                });
                workerThreads.add(t);
                t.start();
            }

            // If should output status, start the status thread
            Thread statusThread = null;
            if (settings.shouldOutputStatus()) {
                statusThread = new Thread(() -> {
                    long lastLoggedMillis = 0;

                    while (!Thread.interrupted()) {
                        long waitTime = settings.outputFreqSecs() * 1000 +
                                lastLoggedMillis - System.currentTimeMillis();
                        if (waitTime > 0) {
                            try {
                                Thread.sleep(waitTime);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                        lastLoggedMillis = System.currentTimeMillis();
                        printStatus();
                    }
                });
                statusThread.start();
            }

            // Wait for search to finish
            lock.lock();
            try {
                while (!searchFinished()) {
                    if (settings.timeLimited()) {
                        long timeRemaining = settings.maxTimeSecs() * 1000 +
                                startTimeMillis - System.currentTimeMillis();
                        if (timeRemaining > 0) {
                            searchFinished.await(timeRemaining,
                                    TimeUnit.MILLISECONDS);
                        }
                    } else {
                        searchFinished.await();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }

            // Interrupt all the threads
            for (Thread t : workerThreads) {
                t.interrupt();
            }

            if (statusThread != null) {
                statusThread.interrupt();
            }

            // Wait for the threads to finish
            try {
                for (Thread t : workerThreads) {
                    t.join();
                }
                if (statusThread != null) {
                    statusThread.join();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } else {
            long lastLoggedMillis = 0;

            while (!searchFinished()) {
                // First, let's print out the status if necessary
                if (settings.shouldOutputStatus() &&
                        System.currentTimeMillis() - lastLoggedMillis >
                                settings.outputFreqSecs() * 1000) {
                    lastLoggedMillis = System.currentTimeMillis();
                    printStatus();
                }

                // Then, run a single worker
                getWorker().run();
            }
        }

        if (settings.shouldOutputStatus()) {
            printStatus();
            System.out.println("Search finished.\n");
        }

        lock.lock();
        try {
            if (results.exceptionalState() != null) {
                results.endCondition(EXCEPTION_THROWN);
            } else if (results.invariantViolatingState() != null) {
                results.endCondition(INVARIANT_VIOLATED);
            } else if (results.goalMatchingState() != null) {
                results.endCondition(GOAL_FOUND);
            } else if (numActiveWorkers == 0 && spaceExhausted()) {
                results.endCondition(SPACE_EXHAUSTED);
            } else {
                results.endCondition(TIME_EXHAUSTED);
            }
        } finally {
            lock.unlock();
        }

        return results;
    }

    public static SearchResults bfs(@NonNull SearchState initialState,
                                    SearchSettings settings) {
        if (settings == null) {
            settings = new SearchSettings();
        }
        return new BFS(settings).run(initialState);
    }

    public static SearchResults dfs(@NonNull SearchState initialState,
                                    SearchSettings settings) {
        if (settings == null) {
            settings = new SearchSettings();
        }
        return new RandomDFS(settings).run(initialState);
    }
}


class BFS extends Search {
    private final Queue<SearchState> queue = new ConcurrentLinkedQueue<>();
    private final Set<SearchEquivalenceWrappedSearchState> discovered =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final AtomicLong states = new AtomicLong();
    private final AtomicInteger depth = new AtomicInteger();

    private final AtomicInteger activeWorkers = new AtomicInteger();
    private int initialDepth, currentDepth;

    BFS(SearchSettings settings) {
        super(settings);
    }

    @Override
    protected String searchType() {
        return "breadth-first";
    }

    @Override
    protected String status(double elapsedSecs) {
        long explored = states.get();
        return String.format("Explored: %s, Depth: %s (%.2fs, %.2fK states/s)",
                explored, depth.get(), elapsedSecs,
                explored / elapsedSecs / 1000.0);
    }

    @Override
    protected void initSearch(SearchState initialState) {
        queue.add(initialState);
        discovered.add(initialState.wrapped());
        states.set(0);
        depth.getAndAccumulate(initialState.depth(), Math::max);
        initialDepth = initialState.depth();
    }

    @Override
    protected boolean spaceExhausted() {
        return queue.isEmpty();
    }

    @Override
    protected Runnable getWorker() {
        final int currentWorkers = activeWorkers.get();
        final SearchState head = queue.peek();

        // Don't start workers for multiple depths at the same time
        if (head == null ||
                (currentWorkers > 0 && head.depth() > currentDepth)) {
            return null;
        }

        // getWorker (and dequeuing) protected by lock; will be same as peek
        final SearchState toExplore = queue.poll();
        assert toExplore != null;

        if (toExplore.depth() > currentDepth) {
            currentDepth = toExplore.depth();
        }
        activeWorkers.incrementAndGet();
        return () -> exploreNode(toExplore);
    }

    private void exploreNode(@NonNull SearchState node) {
        // Check the initial state
        if (node.depth() == initialDepth) {
            states.incrementAndGet();

            StateStatus status = checkState(node, false);

            // For now, don't consider PRUNED initial states
            if (status.equals(StateStatus.TERMINAL)) {
                activeWorkers.decrementAndGet();
                return;
            }
        }

        for (Event event : node.events(settings)) {
            SearchState successor = node.stepEvent(event, settings, true);

            if (successor == null || !discovered.add(successor.wrapped())) {
                continue;
            }

            depth.getAndAccumulate(successor.depth(), Math::max);
            states.incrementAndGet();

            StateStatus status = checkState(successor, false);

            if (status.equals(StateStatus.TERMINAL)) {
                activeWorkers.decrementAndGet();
                return;
            } else if (status.equals(StateStatus.PRUNED)) {
                continue;
            }

            queue.add(successor);
        }
        activeWorkers.decrementAndGet();
    }
}


class RandomDFS extends Search {
    private SearchState initialState;

    private final AtomicLong states = new AtomicLong(), probes =
            new AtomicLong();

    RandomDFS(SearchSettings settings) {
        super(settings);
    }

    @Override
    protected String searchType() {
        return "random depth-first";
    }

    @Override
    protected String status(double elapsedSecs) {
        long explored = states.get();
        if (settings.depthLimited()) {
            return String.format(
                    "Explored: %s, Num Probes: %s (%.2fs, %.2fK explored/s)",
                    explored, probes.get(), elapsedSecs,
                    explored / elapsedSecs / 1000.0);
        } else {
            return String.format("Explored: %s (%.2fs, %.2fK explored/s)",
                    explored, elapsedSecs, explored / elapsedSecs / 1000.0);
        }
    }

    @Override
    protected void initSearch(SearchState initialState) {
        this.initialState = initialState;
        probes.set(0);
        states.set(0);
    }

    @Override
    protected boolean spaceExhausted() {
        return false;
    }

    @Override
    protected Runnable getWorker() {
        return this::runProbe;
    }

    private void runProbe() {
        probes.incrementAndGet();
        states.incrementAndGet();

        for (SearchState current = initialState, next = null; current != null;
             current = next, next = null) {

            List<Event> events = new ArrayList<>(current.events(settings));
            Collections.shuffle(events);

            for (Event event : events) {
                SearchState s = current.stepEvent(event, settings, true);
                if (s == null) {
                    continue;
                }

                states.incrementAndGet();
                StateStatus status = checkState(s, true);

                if (status.equals(StateStatus.TERMINAL)) {
                    return;
                } else if (status.equals(StateStatus.PRUNED)) {
                    continue;
                }

                next = s;
                break;
            }
        }
    }
}
