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

import dslabs.framework.testing.Event;
import dslabs.framework.testing.search.Search;
import dslabs.framework.testing.search.SearchResults;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.List;
import lombok.NonNull;
import org.apache.commons.lang3.NotImplementedException;

class TraceReplaySearch extends Search {
    private SearchState initialState;
    private final List<Event> trace;
    private boolean startedReplay = false, eventsExhausted = false;

    TraceReplaySearch(@NonNull SearchSettings settings,
                      @NonNull List<Event> trace) {
        super(settings);
        assert settings.singleThreaded();
        assert !settings.shouldOutputStatus();
        this.trace = trace;
    }

    @Override
    protected void initSearch(SearchState initialState) {
        this.initialState = initialState;
    }

    @Override
    protected String searchType() {
        throw new NotImplementedException();
    }

    @Override
    protected String status(double elapsedSecs) {
        throw new NotImplementedException();
    }

    @Override
    protected boolean spaceExhausted() {
        return eventsExhausted;
    }

    @Override
    protected Runnable getWorker() {
        if (startedReplay) {
            return null;
        }
        startedReplay = true;
        return this::replayTrace;
    }

    private void replayTrace() {
        SearchState s = initialState;
        if (checkState(s, false) == StateStatus.TERMINAL) {
            return;
        }
        for (Event e : trace) {
            final SearchState prev = s;
            s = s.stepEvent(e, settings, false);
            if (s == null) {
                if (GlobalSettings.verbose()) {
                    System.err.println(
                            "Could not replay trace; event cannot be delivered.\n" +
                                    prev + "\n\t" + e + "\n");
                }
                eventsExhausted = true;
                return;
            }

            StateStatus status = checkState(s, true);
            // replaying a trace should never prune states
            assert status != StateStatus.PRUNED;
            if (status == StateStatus.TERMINAL) {
                return;
            }
        }
        eventsExhausted = true;
    }

    @Override
    protected SearchResults run(SearchState initialState) {
        return super.run(initialState);
    }
}
