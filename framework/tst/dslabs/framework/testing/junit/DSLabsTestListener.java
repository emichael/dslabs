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


import com.google.common.base.Throwables;
import dslabs.framework.testing.utils.GlobalSettings;
import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;
import org.junit.experimental.categories.Category;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

class DSLabsTestListener extends RunListener {
    protected static final String large_sep = StringUtils.repeat('=', 50);
    protected static final String small_sep = StringUtils.repeat('-', 50);

    private final RunNotifier runNotifier;

    private int totalPoints = 0;
    private int pointsEarned = 0;
    private int numPassed = 0;
    private boolean testFailed = false;
    private long startMillis = 0;

    private final PrintStream out = System.out;
    private final PrintStream err = System.err;

    static boolean isInCategory(Description description, Class<?> category) {
        Category cat = description.getAnnotation(Category.class);
        return cat != null && Arrays.asList(cat.value()).contains(category);
    }

    DSLabsTestListener(RunNotifier runNotifier) {
        this.runNotifier = runNotifier;
    }

    static int testNumber(Description d) {
        assert d.isTest();
        String n = d.getMethodName();
        return Integer.parseInt(n.replaceFirst("test(\\d+)\\w+", "$1"));
    }

    static String fullTestNumber(Description d) {
        assert d.isTest();
        Part p = d.getTestClass().getAnnotation(Part.class);
        if (p == null) {
            return Integer.toString(testNumber(d));
        } else {
            return p.value() + "." + testNumber(d);
        }
    }

    @Override
    public void testRunFinished(Result result) {
        out.println(large_sep);
        out.println();
        out.println("Tests passed: " + numPassed + "/" + result.getRunCount());
        out.println(String.format("Points: %s/%s (%.2f%%)", pointsEarned,
                totalPoints, totalPoints != 0 ?
                        100 * ((double) pointsEarned) / ((double) totalPoints) :
                        0));
        out.println("Total time: " + elapsedTimeAsString(result.getRunTime()) +
                "s");
        if (result.wasSuccessful()) {
            out.println("\nALL PASS");
        } else {
            out.println("\nFAIL");
        }
        out.println(large_sep);
    }

    protected void logTestStarted() {
        testFailed = false;
        startMillis = System.currentTimeMillis();
    }

    @Override
    public void testStarted(Description description) {
        logTestStarted();

        out.println(small_sep);
        out.println("TEST " + fullTestNumber(description) + ": " +
                testName(description) + " (" + totalPoints(description) +
                "pts)\n");
        totalPoints += totalPoints(description);
    }

    @Override
    public void testFailure(Failure failure) {
        testFailed = true;

        // If we dropped into the visualization client, halt other tests
        if (isInCategory(failure.getDescription(), SearchTests.class) &&
                failure.getException() instanceof VizClientStarted &&
                GlobalSettings.startVisualization()) {
            // Don't let the main method kill the visualization client
            DSLabsTestCore.preventExitOnFailure();

            runNotifier.pleaseStop();
        } else {
            // Otherwise print the exception
            err.println(
                    Throwables.getStackTraceAsString(failure.getException()));
        }
    }

    @Override
    public void testFinished(Description description) {
        if (!testFailed) {
            pointsEarned += totalPoints(description);
            numPassed++;
            out.print("...PASS");
        } else {
            out.print("...FAIL");
        }
        out.println(" (" +
                elapsedTimeAsString(System.currentTimeMillis() - startMillis) +
                "s)");
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        System.out.println(
                "ASSUMPTION FAILURE: " + testName(failure.getDescription()));
        out.println(small_sep);
    }

    private String testName(Description description) {
        String name;

        TestDescription testDescription =
                description.getAnnotation(TestDescription.class);
        if (testDescription != null) {
            name = testDescription.value();
        } else {
            name = description.getDisplayName();
        }

        if (isInCategory(description, RunTests.class)) {
            name += " [RUN]";
        }

        if (isInCategory(description, SearchTests.class)) {
            name += " [SEARCH]";
        }

        if (isInCategory(description, UnreliableTests.class)) {
            name += " [UNRELIABLE]";
        }

        return name;
    }

    private int totalPoints(Description description) {
        int ret = 0;

        TestPointValue p = description.getAnnotation(TestPointValue.class);
        if (p != null) {
            ret += p.value();
        }

        for (Description child : description.getChildren()) {
            ret += totalPoints(child);
        }

        return ret;
    }

    private String elapsedTimeAsString(long runTime) {
        return NumberFormat.getInstance().format((double) runTime / 1000);
    }
}
