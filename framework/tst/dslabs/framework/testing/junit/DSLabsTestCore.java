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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.runner.Computer;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

public abstract class DSLabsTestCore {
    private static boolean EXIT_ON_TEST_FAILURE = true;

    static void preventExitOnFailure() {
        EXIT_ON_TEST_FAILURE = false;
    }

    public static void main(String[] args)
            throws ClassNotFoundException, NoSuchMethodException,
            InvocationTargetException, IllegalAccessException {

        RunNotifier notifier = new RunNotifier();
        RunListener listener = new DSLabsTestListener(notifier);
        notifier.addListener(listener);

        // Use reflection to parse commandline arguments
        Method method =
                Class.forName("org.junit.runner.JUnitCommandLineParseResult")
                     .getDeclaredMethod("parse", String[].class);
        method.setAccessible(true);
        Object parseResult = method.invoke(null, (Object) args);
        method = Class.forName("org.junit.runner.JUnitCommandLineParseResult")
                      .getDeclaredMethod("createRequest", Computer.class);
        method.setAccessible(true);
        Request request = (Request) method.invoke(parseResult, new Computer());

        Runner runner = request.getRunner();
        Result result = new Result();

        RunListener defaultListener = result.createListener();
        notifier.addFirstListener(defaultListener);

        try {
            notifier.fireTestRunStarted(runner.getDescription());
            runner.run(notifier);
            notifier.fireTestRunFinished(result);
        } finally {
            notifier.removeListener(defaultListener);
        }

        if (EXIT_ON_TEST_FAILURE && !result.wasSuccessful()) {
            System.exit(1);
        }
    }
}