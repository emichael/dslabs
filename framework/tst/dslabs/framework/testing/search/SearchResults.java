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
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Getter;
import lombok.Setter;

@Getter
public class SearchResults {
    public enum EndCondition {
        SPACE_EXHAUSTED, TIME_EXHAUSTED, INVARIANT_VIOLATED, GOAL_FOUND,
        EXCEPTION_THROWN
    }

    // Only set by main thread
    @Setter private Collection<StatePredicate> invariantsTested;
    @Setter private Collection<StatePredicate> goalsSought;
    @Setter private EndCondition endCondition;

    // Set by worker threads
    private final AtomicReference<SearchState> invariantViolatingState =
            new AtomicReference<>();
    private volatile StatePredicate invariantViolated;

    private final AtomicReference<SearchState> goalMatchingState =
            new AtomicReference<>();
    private volatile StatePredicate goalMatched;

    private final AtomicReference<SearchState> exceptionalState =
            new AtomicReference<>();
    private volatile boolean exceptionThrown;

    public SearchState invariantViolatingState() {
        return invariantViolatingState.get();
    }

    public SearchState exceptionalState() {
        return exceptionalState.get();
    }

    public SearchState goalMatchingState() {
        return goalMatchingState.get();
    }

    void invariantViolated(SearchState state, StatePredicate invariant) {
        if (invariantViolatingState.compareAndSet(null, state)) {
            invariantViolated = invariant;
        }
    }

    void goalFound(SearchState state, StatePredicate goal) {
        if (goalMatchingState.compareAndSet(null, state)) {
            goalMatched = goal;
        }
    }

    void exceptionThrown(SearchState state) {
        exceptionThrown = true;
        exceptionalState.compareAndSet(null, state);
    }
}
