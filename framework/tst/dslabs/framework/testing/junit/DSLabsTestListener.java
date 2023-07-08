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
import java.text.NumberFormat;
import org.apache.commons.lang3.StringUtils;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import static dslabs.framework.testing.junit.DSLabsJUnitTest.isInCategory;
import static dslabs.framework.testing.junit.DSLabsTestCore.fullTestNumber;
import static dslabs.framework.testing.junit.VizStartedListener.vizStarted;

class DSLabsTestListener extends RunListener {
    protected static final String large_sep = StringUtils.repeat('=', 50);
    protected static final String small_sep = StringUtils.repeat('-', 50);

    private int totalPoints = 0;
    private int pointsEarned = 0;
    private int numPassed = 0;
    private boolean testFailed = false;
    private long startMillis = 0;

    @Override
    public void testRunFinished(Result result) {
        System.out.println(large_sep);
        System.out.println();
        System.out.println(
                "Tests passed: " + numPassed + "/" + result.getRunCount());
        System.out.printf("Points: %s/%s (%.2f%%)%n", pointsEarned, totalPoints,
                totalPoints != 0 ?
                        100 * ((double) pointsEarned) / ((double) totalPoints) :
                        0);
        System.out.println(
                "Total time: " + elapsedTimeAsString(result.getRunTime()) +
                        "s");
        if (result.wasSuccessful()) {
            System.out.println("\nALL PASS");
        } else {
            System.out.println("\nFAIL");
        }
        System.out.println(large_sep);
    }

    protected void logTestStarted() {
        testFailed = false;
        startMillis = System.currentTimeMillis();
    }

    @Override
    public void testStarted(Description description) {
        logTestStarted();

        System.out.println(small_sep);
        System.out.println("TEST " + fullTestNumber(description) + ": " +
                testName(description) + " (" + totalPoints(description) +
                "pts)\n");
        totalPoints += totalPoints(description);
    }

    @Override
    public void testFailure(Failure failure) {
        testFailed = true;

        // Don't print the failure if the visualizer started.
        if (vizStarted(failure)) {
            return;
        }

        System.err.println(
                Throwables.getStackTraceAsString(failure.getException()));
    }

    @Override
    public void testFinished(Description description) {
        if (!testFailed) {
            pointsEarned += totalPoints(description);
            numPassed++;
            System.out.print("...PASS");
        } else {
            System.out.print("...FAIL");
        }
        System.out.println(" (" +
                elapsedTimeAsString(System.currentTimeMillis() - startMillis) +
                "s)");
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        System.out.println(
                "ASSUMPTION FAILURE: " + testName(failure.getDescription()));
        System.out.println(small_sep);
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
