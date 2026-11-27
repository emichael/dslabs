package dslabs.framework.benchmarks;

import dslabs.framework.Address;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.NodeGenerator;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.runner.RunSettings;
import dslabs.framework.testing.runner.RunState;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.openjdk.jmh.annotations.*;

/**
 * A benchmark that runs two nodes, constantly sending messages to each other while timers are
 * firing. The important output metrics is {@link EventDeliveryCounters#messagesDelivered}, as the
 * timers fire at a fixed rate.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 2)
@Measurement(iterations = 3, batchSize = 1)
public class RunStateBenchmark {

  private static final int TIMER_DURATION_MILLIS = 5;
  private static final Duration ITERATION_DURATION = Duration.ofSeconds(5);

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.OPERATIONS)
  public static class EventDeliveryCounters {
    public long messagesDelivered;
    public long timersFired;
  }

  @Data
  private static class M implements Message {}

  @Data
  private static class T implements Timer {}

  @EqualsAndHashCode(callSuper = true)
  private static final class BenchmarkNode extends Node {
    private final Address peer;

    private long messageDelivered;
    private long timersFired;

    BenchmarkNode(@NonNull Address address, Address peer) {
      super(address);
      this.peer = peer;
    }

    @Override
    public void init() {
      send(new M(), peer);
      set(new T(), TIMER_DURATION_MILLIS);
    }

    private void handleM(M message, Address sender) {
      messageDelivered++;
      send(new M(), sender);
    }

    private void onT(T timer) {
      timersFired++;
      set(timer, TIMER_DURATION_MILLIS);
    }
  }

  private RunState state;
  private RunSettings settings;

  @Setup(Level.Invocation)
  public void setup() {
    LocalAddress a1 = new LocalAddress("node-1");
    LocalAddress a2 = new LocalAddress("node-2");
    state =
        new RunState(
            NodeGenerator.builder()
                .serverSupplier(a -> {
                  Address peer = a.equals(a1) ? a2 : a1;
                  return new BenchmarkNode(a, peer);
                })
                .clientSupplier(() -> null)
                .workloadSupplier(Workload.emptyWorkload())
                .build());
    state.addServer(a1);
    state.addServer(a2);
    settings = new RunSettings();
    settings.maxTimeSecs((int) ITERATION_DURATION.toSeconds());
  }

  private void runBenchmark(EventDeliveryCounters counters) throws InterruptedException {
    state.run(settings);
    for (Node n : state.servers()) {
      BenchmarkNode b = (BenchmarkNode) n;
      counters.messagesDelivered += b.messageDelivered;
      counters.timersFired += b.timersFired;
    }
  }

  @Benchmark
  public void singlethreaded(EventDeliveryCounters counters) throws InterruptedException {
    settings.singleThreaded(true);
    runBenchmark(counters);
  }

  @Benchmark
  public void multithreaded(EventDeliveryCounters counters) throws InterruptedException {
    settings.singleThreaded(false);
    runBenchmark(counters);
  }
}
