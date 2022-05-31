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

package dslabs.framework.testing.utils;

import dslabs.framework.Node;
import dslabs.framework.testing.ClientWorker;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.search.SearchState;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Registers the results of various checks and prints out a report on test
 * shutdown.
 */
public abstract class CheckLogger {
    private static final Class[] ignoredClasses = {Workload.class};

    private static final Map<Class, Object> notEqualToClone =
            new ConcurrentHashMap<>();
    private static final Map<Class, Object> hashCodeNotEqualToClone =
            new ConcurrentHashMap<>();
    private static final Map<Class, Object> notFastCloned =
            new ConcurrentHashMap<>();

    private static final Map<String, Pair<SearchState, Event>>
            notDeterministicMethods = new ConcurrentHashMap<>();
    private static final Map<String, Pair<SearchState, Event>>
            notIdempotentMethods = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(
                new Thread(CheckLogger::printCheckResults,
                        "Check results printer"));
    }

    private static String methodName(Event event, SearchState state) {
        String methodName;
        if (event.isMessage()) {
            methodName = "handle" +
                    event.message().message().getClass().getSimpleName();
        } else if (event.isTimer()) {
            methodName =
                    "on" + event.timer().timer().getClass().getSimpleName();
        } else {
            // Don't handle other methods for now
            return null;
        }

        Node n = state.node(event.locationRootAddress().rootAddress());
        if (n instanceof ClientWorker) {
            // TODO: reflect on ClientWorker to get the name of the client instead
        }

        return n.getClass().getSimpleName() + "." + methodName;
    }

    private static boolean isIgnored(Class c) {
        for (Class<?> o : ignoredClasses) {
            if (o.isAssignableFrom(c)) {
                return true;
            }
        }
        return false;
    }

    public static void notEqualToClone(@NonNull Object object) {
        Class c = object.getClass();
        if (!isIgnored(c)) {
            notEqualToClone.putIfAbsent(c, object);
        }
    }

    public static void hashCodeNotEqualToClone(@NonNull Object object) {
        Class c = object.getClass();
        if (!isIgnored(c)) {
            hashCodeNotEqualToClone.putIfAbsent(c, object);
        }
    }

    public static void notFastCloned(@NonNull Object object) {
        Class c = object.getClass();
        if (!isIgnored(c)) {
            notFastCloned.putIfAbsent(c, object);
        }
    }

    public static void notDeterministic(Event event,
                                        SearchState startingState) {
        String methodName = methodName(event, startingState);
        if (methodName == null) {
            return;
        }

        notDeterministicMethods.putIfAbsent(methodName,
                new ImmutablePair<>(startingState, event));
    }

    public static void notIdempotent(Event event, SearchState startingState) {
        String methodName = methodName(event, startingState);
        if (methodName == null) {
            return;
        }

        notIdempotentMethods.putIfAbsent(methodName,
                new ImmutablePair<>(startingState, event));
    }

    /* Common Tests */
    private static void printCheckResults() {
        if (notEqualToClone.isEmpty() && hashCodeNotEqualToClone.isEmpty() &&
                notDeterministicMethods.isEmpty() &&
                notIdempotentMethods.isEmpty()) {
            return;
        }

        System.err.println();
        System.err.println(StringUtils.repeat("*", 50));
        System.err.println(
                "* Check results" + StringUtils.repeat(" ", 34) + "*");
        System.err.println(StringUtils.repeat("*", 50));
        System.err.println();

        if (!notEqualToClone.isEmpty()) {
            System.err.println(
                    "Objects not equal to their clone. Check all classes correctly implement equals.");
            printClasses(notEqualToClone);
        }

        if (!hashCodeNotEqualToClone.isEmpty()) {
            System.err.println(
                    "Objects have hashCode not equal to their clone. Check all classes correctly implement hashCode.");
            printClasses(hashCodeNotEqualToClone);
        }

        if (!notFastCloned.isEmpty()) {
            System.err.println("Objects cannot be fast-cloned. " +
                    "Check that they don't contain lambdas or other non-standard fields. " +
                    "This error could also occur due to the use of a data structure the fast-cloning library does not yet support.");
            printClasses(notFastCloned);
        }

        if (!notDeterministicMethods.isEmpty()) {
            System.err.println("The following methods are not deterministic:");
            printMethods(notDeterministicMethods);
        }

        if (!notIdempotentMethods.isEmpty()) {
            System.err.println("The following methods are not idempotent:");
            printMethods(notIdempotentMethods);
        }
    }

    private static void printMethods(Map<String, Pair<SearchState, Event>> m) {
        m.forEach((methodName, info) -> System.err.println(
                String.format("- %s\n  See: %s\n       %s", methodName,
                        info.getLeft(), info.getRight())));
        System.err.println();
    }

    private static void printClasses(Map<Class, Object> m) {
        m.forEach((key, value) -> System.err.println(
                String.format("- %s | %s", key, value)));
        System.err.println();
    }
}
