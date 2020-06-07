package dslabs.clientserver;

import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.kvstore.KVStoreWorkload;
import java.util.List;

import static dslabs.clientserver.ClientServerBaseTest.SA;
import static dslabs.clientserver.ClientServerBaseTest.builder;

public class CSVizConfig extends VizConfig {
    @Override
    public SearchState getInitialState(int numServers, int numClients,
                                       List<String> commands) {
        SearchState searchState =
                super.getInitialState(0, numClients, commands);
        searchState.addServer(SA);
        return searchState;
    }

    @Override
    protected StateGenerator stateGenerator(List<String> workload) {
        StateGeneratorBuilder builder = builder();
        builder.workloadSupplier(
                KVStoreWorkload.builder().commandStrings(workload).build());
        return builder.build();
    }
}

