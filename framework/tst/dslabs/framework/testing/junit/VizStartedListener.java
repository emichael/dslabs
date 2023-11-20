package dslabs.framework.testing.junit;

import static dslabs.framework.testing.junit.DSLabsJUnitTest.isInCategory;

import dslabs.framework.testing.utils.GlobalSettings;
import lombok.RequiredArgsConstructor;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

/**
 * Run listener which halts remaining tests and prevents the {@link System#exit} call when the
 * visual debugger starts.
 */
@RequiredArgsConstructor
final class VizStartedListener extends RunListener {
  static boolean vizStarted(Failure failure) {
    return isInCategory(failure.getDescription(), SearchTests.class)
        && failure.getException() instanceof VizStarted
        && GlobalSettings.startVisualization();
  }

  private final RunNotifier runNotifier;

  @Override
  public void testFailure(Failure failure) {
    // If we dropped into the visualization tool, halt other tests.
    if (vizStarted(failure)) {
      // Don't let the main method kill the visualization tool.
      DSLabsTestCore.preventExitOnFailure();

      runNotifier.pleaseStop();
    }
  }
}
