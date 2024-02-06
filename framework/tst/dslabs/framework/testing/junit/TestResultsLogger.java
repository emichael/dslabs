/*
 * Copyright (c) 2023 Ellis Michael (emichael@cs.washington.edu)
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

import com.google.common.collect.ImmutableList;
import dslabs.framework.testing.junit.TestResults.TestResult;
import dslabs.framework.testing.junit.TestResults.TestResult.TestResultBuilder;
import dslabs.framework.testing.junit.TestResults.TestResultsBuilder;
import dslabs.framework.testing.utils.GlobalSettings;
import dslabs.framework.testing.utils.TeeStdOutErr;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import org.junit.experimental.categories.Category;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/** Records results of test runs and, if enabled, logs them to a file. */
class TestResultsLogger extends RunListener {
  private static boolean isListening = false;

  private final TestResultsBuilder testResultsBuilder = TestResults.builder();
  private TestResultBuilder testResultBuilder = null;

  private Description testDescription;

  private static int testNumber(Description d) {
    assert d.isTest();
    String n = d.getMethodName();
    return Integer.parseInt(n.replaceFirst("test(\\d+)\\w+", "$1"));
  }

  @Override
  public void testRunStarted(Description description) throws Exception {
    // Can only run one instance of the listener at a time
    assert !isListening;
    isListening = true;
    testResultsBuilder.startTime(Instant.now());
  }

  @Override
  public void testRunFinished(Result result) throws IOException {
    testResultsBuilder.endTime(Instant.now());
    TestResults results = testResultsBuilder.build();

    // Write out the results to a file if enabled
    if (GlobalSettings.testResultsOutputFile() != null) {
      results.writeJsonToFile(GlobalSettings.testResultsOutputFile());
    }

    assert isListening;
    isListening = false;
  }

  @Override
  public void testStarted(Description description) throws NoSuchMethodException {
    testDescription = description;

    testResultBuilder = TestResult.builder();
    testResultBuilder.startTime(Instant.now());

    Class<?> testClass = description.getTestClass();

    Lab labName = testClass.getAnnotation(Lab.class);
    if (labName != null) {
      testResultBuilder.labName(labName.value());
    }

    Part part = testClass.getAnnotation(Part.class);
    if (part != null) {
      testResultBuilder.part(part.value());
    }

    testResultBuilder.testNumber(testNumber(description));

    TestDescription testDescription = description.getAnnotation(TestDescription.class);
    if (testDescription != null) {
      testResultBuilder.testDescription(testDescription.value());
    }

    TestPointValue testPointValue = description.getAnnotation(TestPointValue.class);
    if (testPointValue != null) {
      testResultBuilder.pointsAvailable(testPointValue.value());
      testResultBuilder.pointsEarned(testPointValue.value());
    }

    testResultBuilder.testMethodName(description.getMethodName());

    Category categories = description.getAnnotation(Category.class);
    if (categories != null) {
      testResultBuilder.testCategories(
          Arrays.stream(categories.value())
              .map(Class::getName)
              .collect(ImmutableList.toImmutableList()));
    }

    // Install tees for System.out and System.err.
    TeeStdOutErr.installTees();
  }

  @Override
  public void testFailure(Failure failure) {
    assert testDescription != null;
    if (testDescription.getAnnotation(TestPointValue.class) != null) {
      testResultBuilder.pointsEarned(0);
    }
  }

  @Override
  public void testFinished(Description description) {
    // Log the test result
    testResultBuilder.endTime(Instant.now());

    var tee = TeeStdOutErr.clearTees();
    testResultBuilder.stdOutLog(tee.stdOut());
    testResultBuilder.stdOutTruncated(tee.stdOutTruncated());
    testResultBuilder.stdErrLog(tee.stdErr());
    testResultBuilder.stdErrTruncated(tee.stdErrTruncated());

    testResultsBuilder.result(testResultBuilder.build());
    testResultBuilder = null;
    testDescription = null;
  }
}
