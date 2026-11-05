package dslabs.paxos;

import static dslabs.paxos.PaxosTest.builder;

import dslabs.framework.Address;
import dslabs.framework.testing.NodeGenerator;
import dslabs.framework.testing.NodeGenerator.NodeGeneratorBuilder;
import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.kvstore.KVStoreWorkload;
import java.util.List;

@Lab("3")
public class PaxosVizConfig extends VizConfig {
  @Override
  protected NodeGenerator nodeGenerator(
      List<Address> servers, List<Address> clients, List<List<String>> workload) {
    final Address[] serverAddresses = servers.toArray(new Address[0]);
    NodeGeneratorBuilder builder = builder(serverAddresses);
    builder.workloadSupplier(
        a -> KVStoreWorkload.builder().commandStrings(workload.get(clients.indexOf(a))).build());
    return builder.build();
  }
}
