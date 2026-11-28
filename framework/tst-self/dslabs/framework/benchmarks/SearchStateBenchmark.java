package dslabs.framework.benchmarks;

import dslabs.framework.Address;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.NodeGenerator;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.search.Search;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 1)
@Measurement(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
public class SearchStateBenchmark {

  private static final Duration ITERATION_DURATION = Duration.ofSeconds(5);
  private static final int TIMER_DURATION_MILLIS = 5;

  @Param({"2", "8", "128"})
  private int numNodes;

  @State(Scope.Thread)
  @AuxCounters(AuxCounters.Type.OPERATIONS)
  public static class SearchCounters {
    public long statesExplored;
    public long messagesDelivered;
    public long timersDelivered;
  }

  private static class SearchCountersTracker {
    public LongAdder messagesDelivered = new LongAdder();
    public LongAdder timersDelivered = new LongAdder();
  }

  private static SearchCountersTracker tracker;

  @EqualsAndHashCode(callSuper = true)
  private static final class BenchmarkNode extends Node {
    @Data
    private static class M implements Message {}

    @Data
    private static class T implements Timer {}

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
      tracker.messagesDelivered.increment();
      send(new M(), sender);
    }

    private void onT(T timer) {
      timersFired++;
      tracker.timersDelivered.increment();
      set(timer, TIMER_DURATION_MILLIS);
    }
  }

  private SearchState state;
  private SearchSettings settings;

  @Setup(Level.Invocation)
  public void setup() {
    List<Address> addresses = new ArrayList<>();
    for (int i = 1; i <= numNodes; i++) {
      addresses.add(new LocalAddress("node-" + i));
    }
    state =
        new SearchState(
            NodeGenerator.builder()
                .serverSupplier(
                    a -> {
                      Address peer = addresses.get((addresses.indexOf(a) + 1) % addresses.size());
                      return new BenchmarkNode(a, peer);
                    })
                .clientSupplier(() -> null)
                .workloadSupplier(Workload.emptyWorkload())
                .build());
    for (Address a : addresses) {
      state.addServer(a);
    }
    settings = new SearchSettings();
    settings.maxTimeSecs((int) ITERATION_DURATION.toSeconds());
    settings.outputFreqSecs(-1);
    tracker = new SearchCountersTracker();
  }

  private void runBenchmark(SearchCounters counters) throws InterruptedException {
    Search.bfs(state, settings);
    counters.messagesDelivered = tracker.messagesDelivered.sum();
    counters.timersDelivered = tracker.timersDelivered.sum();
    counters.statesExplored = counters.messagesDelivered + counters.timersDelivered;
  }

  @Benchmark
  public void singlethreaded(SearchCounters counters) throws InterruptedException {
    settings.singleThreaded(true);
    runBenchmark(counters);
  }

  @Benchmark
  public void multithreaded(SearchCounters counters) throws InterruptedException {
    settings.singleThreaded(false);
    runBenchmark(counters);
  }
}
