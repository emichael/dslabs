package dslabs.paxos;

import static dslabs.paxos.PaxosTest.builder;

import dslabs.framework.Address;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.kvstore.KVStoreWorkload;
import java.util.List;

@Lab("3")
public class PaxosVizConfig extends VizConfig {
  @Override
  protected StateGenerator stateGenerator(
      List<Address> servers, List<Address> clients, List<List<String>> workload) {
    final Address[] serverAddresses = servers.toArray(new Address[0]);
    StateGeneratorBuilder builder = builder(serverAddresses);
    builder.workloadSupplier(
        a -> KVStoreWorkload.builder().commandStrings(workload.get(clients.indexOf(a))).build());
    return builder.build();
  }
}
