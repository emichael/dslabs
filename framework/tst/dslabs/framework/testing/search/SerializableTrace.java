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
import dslabs.framework.testing.utils.GlobalSettings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Fully serializable object containing the configuration information, full
 * event history, and correctness checking information. Used to save and replay
 * model checking test case failures.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public class SerializableTrace implements Serializable {
    // Increment this when compatability is broken
    @Serial private static final long serialVersionUID = 42L;


    private final static String TRACE_DIR_NAME = "traces",
            TRACE_FILE_EXTENSION = ".trace";

    private final List<Event> history;
    private final Collection<StatePredicate> invariants;
    private final StateGenerator stateGenerator;
    private final Collection<Address> servers;
    private final Collection<Pair<Address, Workload>> clientWorkers;

    @NonNull private final String labId;
    private final Integer labPart;

    private final String testClassName;
    private final String testMethodName;

    private final LocalDateTime createdDate = LocalDateTime.now();

    private transient String fileName = null;

    private static void ensureTraceDirExists() {
        final File traceDir = new File(TRACE_DIR_NAME);
        if (!traceDir.exists() || !traceDir.isDirectory()) {
            if (!traceDir.mkdirs()) {
                throw new RuntimeException(
                        "Could not create directory " + traceDir);
            }
        }
    }

    private String defaultBaseName() {
        final String dateString =
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
                                 .format(createdDate);
        return String.format("lab%s%s_%s", labId,
                labPart == null ? "" : "part" + labPart, dateString);
    }

    private Path savePath() {
        final String baseName = defaultBaseName();
        Path filePath;
        int n = 0;
        do {
            if (n == 0) {
                filePath = Path.of(TRACE_DIR_NAME,
                        String.format("%s%s", baseName, TRACE_FILE_EXTENSION));
            } else {
                filePath = Path.of(TRACE_DIR_NAME,
                        String.format("%s_%s%s", baseName, n,
                                TRACE_FILE_EXTENSION));
            }
            n++;
        } while (Files.exists(filePath));

        return filePath;
    }

    void save() {
        ensureTraceDirExists();
        final Path filePath = savePath();
        try (ObjectOutputStream traceFile = new ObjectOutputStream(
                new FileOutputStream(filePath.toString()))) {
            traceFile.writeObject(this);
            if (GlobalSettings.verbose()) {
                System.out.println("Saved trace to " + filePath + "\n");
            }
        } catch (IOException e) {
            System.err.println("Could not save trace");
            e.printStackTrace();
            System.err.println();
        }
    }

    public SearchState initialState() {
        SearchState ret = new SearchState(stateGenerator);
        for (Address a : servers) {
            ret.addServer(a);
        }

        for (Pair<Address, Workload> p : clientWorkers) {
            ret.addClientWorker(p.getLeft(), p.getRight());
        }

        return ret;
    }

    public SearchState endState() {
        SearchState s = initialState();
        for (Event e : history) {
            s = s.stepEvent(e, null, false);
            if (s == null) {
                return null;
            }
        }
        return s;
    }

    private boolean replays() {
        return endState() != null;
    }

    private static Path[] traceFilePaths() {
        final File traceDir = new File(TRACE_DIR_NAME);
        if (!traceDir.exists() || !traceDir.isDirectory()) {
            return new Path[0];
        }

        return Arrays.stream(Objects.requireNonNull(traceDir.list(
                             (dir, name) -> name.endsWith(TRACE_FILE_EXTENSION))))
                     .map(s -> Path.of(TRACE_DIR_NAME, s)).toArray(Path[]::new);
    }

    private static SerializableTrace loadTrace(Path tracePath) {
        SerializableTrace trace;
        try (ObjectInputStream is = new ObjectInputStream(
                new FileInputStream(tracePath.toFile()))) {
            trace = (SerializableTrace) is.readObject();
            trace.fileName = tracePath.getFileName().toString();
        } catch (ClassNotFoundException | IOException e) {
            if (GlobalSettings.verbose()) {
                System.err.println("Trace " + tracePath.getFileName() +
                        " no longer loads; message/timer definitions may have changed");
            }
            return null;
        }
        return trace;
    }

    public static SerializableTrace loadTrace(String traceFileName) {
        final Path defaultPath = Path.of(traceFileName);

        // If traceFileName starts with a base name, consider the same path in traces/
        final Path pathInDir =
                traceFileName.startsWith(".") || traceFileName.startsWith("/") ?
                        defaultPath : Path.of(TRACE_DIR_NAME, traceFileName);

        final Path path =
                defaultPath.toFile().exists() ? defaultPath : pathInDir;

        if (!path.toFile().exists()) {
            System.err.println("Could not find trace file: " + traceFileName);
            return null;
        }

        return loadTrace(path);
    }

    public static SerializableTrace[] traces() {
        return Arrays.stream(traceFilePaths()).map(SerializableTrace::loadTrace)
                     .filter(Objects::nonNull)
                     .toArray(SerializableTrace[]::new);
    }
}
