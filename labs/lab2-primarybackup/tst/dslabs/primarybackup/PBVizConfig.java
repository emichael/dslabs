package dslabs.primarybackup;

import static dslabs.primarybackup.PrimaryBackupTest.builder;
import static dslabs.primarybackup.ViewServerTest.VSA;

import dslabs.framework.Address;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.kvstore.KVStoreWorkload;
import java.util.List;

@Lab("2")
public class PBVizConfig extends VizConfig {
  @Override
  public SearchState getInitialState(int numServers, int numClients, List<List<String>> commands) {
    SearchState searchState = super.getInitialState(numServers, numClients, commands);
    searchState.addServer(VSA);
    return searchState;
  }

  @Override
  protected StateGenerator stateGenerator(
      List<Address> servers, List<Address> clients, List<List<String>> workload) {
    StateGeneratorBuilder builder = builder();
    builder.workloadSupplier(
        a -> KVStoreWorkload.builder().commandStrings(workload.get(clients.indexOf(a))).build());
    return builder.build();
  }
}
