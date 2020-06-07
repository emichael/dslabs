package dslabs.clientserver;

import dslabs.framework.Address;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.junit.BaseJUnitTest;
import dslabs.framework.testing.runner.RunState;
import dslabs.framework.testing.search.SearchState;
import dslabs.kvstore.KVStore;
import dslabs.kvstore.KVStoreWorkload;
import java.util.Objects;

abstract class ClientServerBaseTest extends BaseJUnitTest {
    static final Address SA = new LocalAddress("server");

    static StateGeneratorBuilder builder() {
        StateGeneratorBuilder builder = StateGenerator.builder();
        builder.serverSupplier(a -> {
            if (!Objects.equals(a, SA)) {
                throw new IllegalArgumentException();
            }
            return new SimpleServer(SA, new KVStore());
        });
        builder.clientSupplier(a -> new SimpleClient(a, SA));
        builder.workloadSupplier(KVStoreWorkload.emptyWorkload());
        return builder;
    }

    @Override
    protected void setupRunTest() {
        runState = new RunState(builder().build());
        runState.addServer(SA);
    }

    @Override
    protected void setupSearchTest() {
        initSearchState = new SearchState(builder().build());
        initSearchState.addServer(SA);
    }
}

