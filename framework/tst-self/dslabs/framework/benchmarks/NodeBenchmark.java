package dslabs.framework.benchmarks;

import dslabs.framework.Address;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import dslabs.framework.testing.LocalAddress;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
public class NodeBenchmark {

  @Data
  private static final class IncrementMessageCount implements Message {}

  @Data
  private static final class IncrementTimerCount implements Timer {}

  @EqualsAndHashCode(callSuper = true)
  private static final class CounterNode extends Node {
    int messagesReceived = 0;
    int timersReceived = 0;

    CounterNode(@NonNull Address address) {
      super(address);
    }

    @Override
    public void init() {}

    private void handleIncrementMessageCount(IncrementMessageCount message, Address sender) {
      messagesReceived++;
    }

    private void onIncrementTimerCount(IncrementTimerCount timer) {
      timersReceived++;
    }
  }

  private Address nodeAddress, otherAddress;
  private CounterNode node;

  @Setup(Level.Trial)
  public void setup() {
    nodeAddress = new LocalAddress("test-node");
    node = new CounterNode(nodeAddress);
    node.init();
    otherAddress = new LocalAddress("other");
  }

  @Benchmark
  @OperationsPerInvocation(1_000)
  public int messageDelivery() {
    IncrementMessageCount message = new IncrementMessageCount();
    for (int i = 0; i <= 1_000; i++) {
      node.handleMessage(message, otherAddress, nodeAddress);
    }
    return node.messagesReceived;
  }

  @Benchmark
  @OperationsPerInvocation(1_000)
  public int timerDelivery() {
    IncrementTimerCount timer = new IncrementTimerCount();
    for (int i = 0; i <= 1_000; i++) {
      node.onTimer(timer, nodeAddress);
    }
    return node.messagesReceived;
  }
}
