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
import dslabs.framework.testing.Event;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.Workload;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Fully serializable object containing the configuration information, full
 * event history, and correctness checking information. Used to save and replay
 * model checking test case failures.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter(AccessLevel.PACKAGE)
class SerializableTrace implements Serializable {
    final static String TRACE_DIR_NAME = "traces";

    private final List<Event> history;
    private final Collection<StatePredicate> invariants;
    private final StateGenerator stateGenerator;
    private final Collection<Address> servers;
    private final Collection<Pair<Address, Workload>> clientWorkers;

    private final LocalDateTime createdDate = LocalDateTime.now();

    void save() {
        // Create the trace directory if it doesn't exist
        final File traceDir = new File(TRACE_DIR_NAME);
        if (!traceDir.exists()) {
            if (!traceDir.mkdirs()) {
                throw new RuntimeException(
                        "Could not create directory " + traceDir);
            }
        }

        final String baseName = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
                                                 .format(createdDate);
        Path filePath;
        int n = 0;
        do {
            filePath = Path.of(TRACE_DIR_NAME,
                    String.format("%s_%s.trace", baseName, n));
            n++;
        } while (Files.exists(filePath));

        try (ObjectOutputStream traceFile = new ObjectOutputStream(
                new FileOutputStream(filePath.toString()))) {
            traceFile.writeObject(this);
            System.err.println("Saved trace to " + filePath + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}


class TraceReplay extends Search {
    private SearchState initialState;
    private final List<Event> trace;
    private boolean startedReplay = false, eventsExhausted = false;

    TraceReplay(@NonNull SearchSettings settings, @NonNull List<Event> trace) {
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
        checkState(s, false);
        for (Event e : trace) {
            s = s.stepEvent(e, settings, false);
            if (s == null) {
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
}
