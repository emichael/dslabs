/*
 * Copyright (c) 2019 Ellis Michael (emichael@cs.washington.edu)
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
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.StatePredicate.PredicateResult;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

abstract class TraceMinimizer {
    static SearchState minimizeTrace(SearchState state,
                                     final PredicateResult expectedResult) {
        // TODO: maintain set of "bad" states so we don't have to unwind every time
        boolean shortenedEventsList;
        do {
            shortenedEventsList = false;
            LinkedList<Event> events = new LinkedList<>();
            for (SearchState s = state; s.previous() != null;
                 s = s.previous()) {
                SearchState test = applyEvents(s.previous(), events);
                if (stateMatches(test, expectedResult)) {
                    shortenedEventsList = true;
                    state = test;
                } else {
                    events.addFirst(s.previousEvent());
                }
            }
        } while (shortenedEventsList);
        return state;
    }

    private static boolean stateMatches(final SearchState s,
                                        final PredicateResult r) {
        if (s == null) {
            return false;
        }
        if (r.exceptionThrown()) {
            return r.predicate().test(s).exceptionThrown();
        }
        final PredicateResult r2 = r.predicate().test(s, !r.value());
        return r2 != null && !r2.exceptionThrown();
    }

    /**
     * Returns a state that results in an exception of the same class as the
     * original one.
     *
     * @param state
     *         the state that throws an exception
     * @return another state throwing the same type of exception
     */
    static SearchState minimizeExceptionCausingTrace(SearchState state) {
        final Throwable exception = state.thrownException();
        assert exception != null;

        StatePredicate exceptionWasThrown =
                StatePredicate.statePredicate(null, s -> {
                    if (!(s instanceof SearchState)) {
                        return false;
                    }

                    Throwable e = ((SearchState) s).thrownException();
                    if (e == null) {
                        return false;
                    }

                    return Objects.equals(e.getClass(), exception.getClass());
                });

        PredicateResult r = exceptionWasThrown.test(state);
        assert r.value();

        return minimizeTrace(state, r);
    }

    private static SearchState applyEvents(SearchState initialState,
                                           List<Event> events) {
        SearchState s = initialState;
        for (Event e : events) {
            // TODO: don't use null settings here, it's re-initialized every time
            // TODO: do we need to use same settings as the search?
            SearchState next = s.stepEvent(e, null, false);
            if (next == null) {
                break;
            }
            s = next;
        }

        return s;
    }
}
