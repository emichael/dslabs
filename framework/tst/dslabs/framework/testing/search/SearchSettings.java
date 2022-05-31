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

import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.StatePredicate.PredicateResult;
import dslabs.framework.testing.TestSettings;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

/**
 * Collection of settings used by the search tests.
 *
 * Safe for concurrent access.
 */
@Getter
@Setter
@Log
public class SearchSettings extends TestSettings<SearchSettings>
        implements Cloneable {
    private volatile int maxDepth = -1;
    private volatile int numThreads = defaultNumThreads();
    private volatile int outputFreqSecs = GlobalSettings.verbose() ? 5 : -1;

    private final Collection<StatePredicate> prunes =
            new ConcurrentLinkedQueue<>();
    private final Collection<StatePredicate> goals =
            new ConcurrentLinkedQueue<>();


    private static int defaultNumThreads() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    protected final SearchSettings self() {
        return this;
    }

    public final SearchSettings addPrune(StatePredicate prune) {
        prunes.add(prune);
        return this;
    }

    public final SearchSettings clearPrunes() {
        prunes.clear();
        return this;
    }

    /**
     * Whether the state should be pruned. Currently, exceptions thrown during
     * prune evaluation are logged, and the state is pruned.
     *
     * @param state
     *         the state to check
     * @return true if any prune predicates match the state
     */
    public final boolean shouldPrune(SearchState state) {
        for (StatePredicate p : prunes) {
            PredicateResult r = p.test(state, false);
            if (r == null) {
                continue;
            }
            /*
                TODO: actually treat this as an error and have it stop the
                      search. This is going to require a bit of re-architecture
                      in the way search works (and even has implications for how
                      traces are saved and the visual debugger works).

                For now, we log the error and treat the state as pruned. This is
                because some searches rely on states being pruned for
                correctness. It is always safe to ignore more states, but if the
                search is allowed to examine states it shouldn't, it could
                report erroneous results.

                Below, we do the same with goals, but there, predicates throwing
                exceptions are simply ignored.

                The issue is not that important; very few predicates *can* throw
                exceptions (possibly only the Paxos interface predicates), but
                it's still important to deal with.
             */
            if (r.exceptionThrown()) {
                LOG.severe(r.errorMessage());
            }
            return true;
        }
        return false;
    }

    public final SearchSettings addGoal(StatePredicate goal) {
        goals.add(goal);
        return this;
    }

    public final SearchSettings clearGoals() {
        goals.clear();
        return this;
    }

    /**
     * The result of any goal predicate which matches the state, or {@code null}
     * if none does. Currently, exceptions thrown during goal evaluation are
     * logged to stderr and ignored.
     *
     * @param state
     *         the state to check
     * @return the result or {@code null}
     */
    public final PredicateResult goalMatched(SearchState state) {
        for (StatePredicate p : goals) {
            PredicateResult r = p.test(state, false);
            if (r == null) {
                continue;
            }
            // TODO: see above
            if (r.exceptionThrown()) {
                LOG.severe(r.errorMessage());
                continue;
            }
            return r;
        }
        return null;
    }

    @Override
    public SearchSettings singleThreaded(boolean singleThreaded) {
        super.singleThreaded(singleThreaded);

        if (singleThreaded) {
            numThreads = 1;
        } else {
            numThreads = defaultNumThreads();
        }
        return this;
    }

    @Override
    public boolean singleThreaded() {
        return numThreads <= 1;
    }

    @Override
    public boolean multiThreaded() {
        return !singleThreaded();
    }

    public boolean shouldOutputStatus() {
        return outputFreqSecs > 0;
    }

    public boolean depthLimited() {
        return maxDepth >= 0;
    }

    @Override
    public SearchSettings maxTimeSecs(int maxTimeSecs) {
        super.maxTimeSecs(maxTimeSecs);
        return this;
    }

    @Override
    public SearchSettings clear() {
        super.clear();
        clearPrunes();
        clearGoals();
        maxDepth(-1);
        outputFreqSecs(5);
        numThreads(defaultNumThreads());
        return this;
    }

    public SearchSettings() {
    }

    private SearchSettings(SearchSettings s) {
        super(s);
        goals.addAll(s.goals);
        prunes.addAll(s.prunes);
        maxDepth = s.maxDepth;
        numThreads = s.numThreads;
        outputFreqSecs = s.outputFreqSecs;
    }

    @Override
    public SearchSettings clone() {
        return new SearchSettings(this);
    }
}
