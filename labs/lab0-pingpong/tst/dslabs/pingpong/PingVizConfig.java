package dslabs.pingpong;

import dslabs.framework.Address;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.pingpong.PingApplication.Ping;
import java.util.List;
import java.util.stream.Collectors;

import static dslabs.pingpong.PingTest.builder;
import static dslabs.pingpong.PingTest.sa;

public class PingVizConfig extends VizConfig {
    @Override
    public SearchState getInitialState(int numServers, int numClients,
                                       List<List<String>> commands) {
        SearchState searchState =
                super.getInitialState(0, numClients, commands);
        searchState.addServer(sa);
        return searchState;
    }

    @Override
    protected StateGenerator stateGenerator(List<Address> servers,
                                            List<Address> clients,
                                            List<List<String>> workload) {
        StateGeneratorBuilder builder = builder();
        builder.workloadSupplier(a -> Workload.workload(
            workload.get(clients.indexOf(a)).stream().map(Ping::new).collect(Collectors.toList())));
        return builder.build();
    }
}
