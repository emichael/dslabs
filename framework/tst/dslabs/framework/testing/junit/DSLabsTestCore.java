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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import dslabs.framework.testing.utils.ClassSearch;
import dslabs.framework.testing.utils.GlobalSettings;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.experimental.categories.Categories.CategoryFilter;
import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runner.notification.StoppedByUserException;

public final class DSLabsTestCore {
  private static boolean EXIT_ON_TEST_FAILURE = true;

  static void preventExitOnFailure() {
    EXIT_ON_TEST_FAILURE = false;
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

  private static void runRequest(Request request, RunListener printer, RunListener... listeners) {
    final Runner runner = request.getRunner();
    final Result result = new Result();
    final RunNotifier notifier = new RunNotifier();

    // TODO: why is this here? Is this just vestigial from copy-paste JUnit code?
    if (runner instanceof ErrorReportingRunner) {
      notifier.addListener(
          new RunListener() {
            @Override
            public void testFailure(Failure failure) {
              System.err.println(Throwables.getStackTraceAsString(failure.getException()));
            }
          });
    } else {
      notifier.addListener(printer);
    }

    for (RunListener listener : listeners) {
      notifier.addListener(listener);
    }

    // Stop the tests when the visual debugger starts.
    notifier.addListener(new VizStartedListener(notifier));

    // TODO: same question, why is this here?
    final RunListener defaultListener = result.createListener();
    notifier.addFirstListener(defaultListener);

    try {
      notifier.fireTestRunStarted(runner.getDescription());
      runner.run(notifier);
      notifier.fireTestRunFinished(result);
    } catch (StoppedByUserException ignored) {
      // This shouldn't bubble up and print for the user to see.
    } finally {
      notifier.removeListener(defaultListener);
    }

    if (EXIT_ON_TEST_FAILURE && !result.wasSuccessful()) {
      System.exit(1);
    }
  }

  public static void main(String[] args) throws Exception {
    final Options options = new Options();
    final Option lab =
        Option.builder("l")
            .longOpt("lab")
            .argName("LAB")
            .hasArg(true)
            .numberOfArgs(1)
            .desc("lab identifier")
            .build();
    final Option part =
        Option.builder("p")
            .longOpt("part")
            .required(false)
            .hasArg(true)
            .numberOfArgs(1)
            .argName("PART_NUMS")
            .desc("comma-separated list of part numbers")
            .build();
    final Option testNum =
        Option.builder("n")
            .longOpt("test-num")
            .required(false)
            .hasArg(true)
            .numberOfArgs(1)
            .argName("TEST_NUMS")
            .desc("comma-separated list of test numbers to run")
            .build();
    final Option excludeRunTests = Option.builder().longOpt("exclude-run-tests").build();
    final Option excludeSearchTests = Option.builder().longOpt("exclude-search-tests").build();
    final Option replaySavedTraces = Option.builder().longOpt("replay-traces").build();

    options.addOption(lab);
    options.addOption(part);
    options.addOption(testNum);
    options.addOption(excludeRunTests);
    options.addOption(excludeSearchTests);
    options.addOption(replaySavedTraces);

    final CommandLineParser parser = new DefaultParser();
    CommandLine line = null;
    try {
      line = parser.parse(options, args, true);
    } catch (ParseException e) {
      System.exit(1);
      return;
    }

    if (line.hasOption(replaySavedTraces)) {
      if (line.getArgs().length > 0) {
        CheckSavedTracesTest.traceNames(line.getArgs());
      }

      if (line.hasOption(lab)) {
        CheckSavedTracesTest.labId(line.getOptionValue(lab));
      }

      if (line.hasOption(part)) {
        CheckSavedTracesTest.labPart(Integer.parseInt(line.getOptionValue(part)));
      }

      Request request = Request.classes(CheckSavedTracesTest.class);
      runRequest(request, new ReplaySavedTracesTestResultsPrinter());
      return;
    }

    final String labID = line.getOptionValue(lab);

    Request request = Request.classes(ClassSearch.testClasses());

    // Only run test classes for this lab
    request =
        request.filterWith(
            new Filter() {
              @Override
              public boolean shouldRun(Description description) {
                if (!description.isSuite()) {
                  return true;
                }
                var l = description.getAnnotation(Lab.class);
                return l != null && l.value().equals(labID);
              }

              @Override
              public String describe() {
                return "lab " + labID;
              }
            });

    final ImmutableSet<String> partNumbers =
        line.hasOption(part)
            ? ImmutableSet.copyOf(line.getOptionValue(part).split(","))
            : ImmutableSet.of();

    // Only run test classes for this part
    if (!partNumbers.isEmpty()) {
      request =
          request.filterWith(
              new Filter() {
                @Override
                public boolean shouldRun(Description description) {
                  if (!description.isSuite()) {
                    return true;
                  }
                  var p = description.getAnnotation(Part.class);
                  return p != null && partNumbers.contains(String.valueOf(p.value()));
                }

                @Override
                public String describe() {
                  return String.format("part %s", partNumbers);
                }
              });
    }

    // Only run chosen test numbers
    if (line.hasOption(testNum)) {
      final ImmutableSet<String> specifiedTestNumbers =
          ImmutableSet.copyOf(line.getOptionValue(testNum).split(","));
      final Set<String> testNumbers = Sets.newHashSet(specifiedTestNumbers);

      // If the part is selected, allow specifying test number only
      if (!partNumbers.isEmpty()) {
        if (partNumbers.size() > 1) {
          throw new IllegalArgumentException(
              "Cannot specify multiple part numbers when selecting individual test numbers.");
        }
        final String partStr = partNumbers.stream().findFirst().get();
        for (var tn : specifiedTestNumbers) {
          // XXX: not the best way of doing this, but it works
          testNumbers.add(partStr + "." + tn);
        }
      }

      request =
          request.filterWith(
              new Filter() {
                @Override
                public boolean shouldRun(Description description) {
                  if (!description.isTest()) {
                    return true;
                  }
                  return testNumbers.contains(fullTestNumber(description));
                }

                @Override
                public String describe() {
                  return String.format("test numbers %s", specifiedTestNumbers);
                }
              });
    }

    if (line.hasOption(excludeRunTests)) {
      request = request.filterWith(CategoryFilter.exclude(RunTests.class));
    }
    if (line.hasOption(excludeSearchTests)) {
      request = request.filterWith(CategoryFilter.exclude(SearchTests.class));
    }

    // Sort methods and test classes
    request = request.sortWith(new TestOrder());

    if (GlobalSettings.testResultsOutputFile() != null) {
      // For now, only attach a TestResultsLogger if we're actually logging to file.
      runRequest(request, new TestResultsPrinter(), new TestResultsLogger());
    } else {
      runRequest(request, new TestResultsPrinter());
    }
  }

  private DSLabsTestCore() {
    throw new UnsupportedOperationException();
  }
}
