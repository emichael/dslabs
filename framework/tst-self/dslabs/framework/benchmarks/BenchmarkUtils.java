package dslabs.framework.benchmarks;

import java.time.Duration;
import lombok.experimental.UtilityClass;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.runner.Defaults;

@UtilityClass
public final class BenchmarkUtils {
  public static Duration benchmarkDuration(Class<?> c) {
    Measurement m = c.getAnnotation(Measurement.class);
    if (m.time() > 0) {
      return Duration.of(m.time(), m.timeUnit().toChronoUnit());
    }
    return Duration.of(
        Defaults.MEASUREMENT_TIME.getTime(),
        Defaults.MEASUREMENT_TIME.getTimeUnit().toChronoUnit());
  }
}
