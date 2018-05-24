package dslabs.primarybackup;

import dslabs.framework.Address;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.visualization.VizConfig;
import dslabs.kvstore.KVStoreWorkload;
import java.util.List;

import static dslabs.primarybackup.PrimaryBackupTest.builder;

public class PBVizConfig extends VizConfig {
    private static final Address vsa = new LocalAddress("viewserver");

    @Override
    public SearchState getInitialState(int numServers, int numClients,
                                       List<String> commands) {
        SearchState searchState =
                super.getInitialState(numServers, numClients, commands);
        searchState.addServer(vsa);
        return searchState;
    }

    @Override
    protected StateGenerator stateGenerator(List<String> commands) {
        StateGeneratorBuilder builder = builder();
        builder.workloadSupplier(
                KVStoreWorkload.builder().commandStrings(commands).build());
        return builder.build();
    }
}

