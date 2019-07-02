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
    static final Address sa = new LocalAddress("server");

    @Override
    protected void setupTest() {
        builder = builder();

        runState = new RunState(builder.build());
        runState.addServer(sa);

        initSearchState = new SearchState(builder.build());
        initSearchState.addServer(sa);
    }

    protected static StateGeneratorBuilder builder() {
        StateGeneratorBuilder builder = StateGenerator.builder();
        builder.serverSupplier(a -> {
            if (!Objects.equals(a, sa)) {
                throw new IllegalArgumentException();
            }
            return new SimpleServer(sa, new KVStore());
        });
        builder.clientSupplier(a -> new SimpleClient(a, sa));
        builder.workloadSupplier(KVStoreWorkload.emptyWorkload());

        return builder;
    }
}

