package dslabs.shardkv;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.junit.PrettyTestName;
import dslabs.framework.testing.junit.RunTests;
import dslabs.framework.testing.junit.SearchTests;
import dslabs.framework.testing.junit.TestPointValue;
import dslabs.framework.testing.junit.UnreliableTests;
import dslabs.kvstore.TransactionalKVStore.MultiGetResult;
import dslabs.kvstore.TransactionalKVStoreWorkload;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import static dslabs.framework.testing.StatePredicate.RESULTS_OK;
import static dslabs.framework.testing.StatePredicate.resultsHaveType;
import static dslabs.kvstore.TransactionalKVStoreWorkload.MULTI_GETS_MATCH;
import static dslabs.kvstore.TransactionalKVStoreWorkload.OK;
import static dslabs.kvstore.TransactionalKVStoreWorkload.multiGet;
import static dslabs.kvstore.TransactionalKVStoreWorkload.multiGetResult;
import static dslabs.kvstore.TransactionalKVStoreWorkload.multiPut;
import static dslabs.kvstore.TransactionalKVStoreWorkload.multiPutOk;
import static dslabs.kvstore.TransactionalKVStoreWorkload.swap;
import static dslabs.kvstore.TransactionalKVStoreWorkload.swapOk;
import static junit.framework.TestCase.assertFalse;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ShardStorePart2Test extends ShardStoreBaseTest {
    @Test(timeout = 5 * 1000)
    @PrettyTestName("Single group, simple transactional workload")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test01SingleBasic() throws InterruptedException {
        int numGroups = 1, numServersPerGroup = 3, numShardMasters = 3,
                numShards = 2;

        setupStates(numGroups, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        joinGroup(1, numServersPerGroup);
        runState.addClientWorker(client(1),
                TransactionalKVStoreWorkload.simpleWorkload);

        runState.waitFor();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Multi-group, simple transactional workload")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test02MultiBasic() throws InterruptedException {
        int numGroups = 2, numServersPerGroup = 3, numShardMasters = 3,
                numShards = 2;

        setupStates(numGroups, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        joinGroup(1, numServersPerGroup);
        joinGroup(2, numServersPerGroup);
        assertConfigBalanced();

        runState.addClientWorker(client(1),
                TransactionalKVStoreWorkload.simpleWorkload);

        runState.waitFor();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("No progress when groups can't communicate")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test03NoProgress() throws InterruptedException {
        int numServersPerGroup = 3, numShardMasters = 3, numShards = 2;

        setupStates(2, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);
        joinGroup(1, numServersPerGroup);
        joinGroup(2, numServersPerGroup);
        assertConfigBalanced();

        Client client = runState.addClient(client(1));
        sendCommandAndCheck(client,
                multiPut("key1-1", "foo1", "key1-2", "foo2"), multiPutOk());

        // Let the previous transaction result propagate
        Thread.sleep(1000);

        // Client can talk to both groups, but they can't talk to each other
        runSettings.partition(servers(1, numServersPerGroup),
                servers(2, numServersPerGroup));
        for (int g = 1; g <= 2; g++) {
            for (Address server : servers(g, numServersPerGroup)) {
                runSettings.linkActive(client(1), server, true);
                runSettings.linkActive(server, client(1), true);
            }
        }

        // Send command to each group
        sendCommandAndCheck(client,
                multiPut("key2-1", "foo1", "key3-1", "foo2"), multiPutOk());
        sendCommandAndCheck(client,
                multiPut("key2-2", "foo1", "key3-2", "foo2"), multiPutOk());

        // Send command to both
        client.sendCommand(multiPut("key4-1", "foo1", "key4-2", "foo2"));

        Thread.sleep(5000);

        // Make sure the last command didn't get executed
        assertFalse(client.hasResult());
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Isolation between MultiPuts and MultiGets")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test04PutGetIsolation() throws InterruptedException {
        int numGroups = 2, numServersPerGroup = 3, numShardMasters = 3,
                numShards = 2, numRounds = 100;

        setupStates(numGroups, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        joinGroup(1, numServersPerGroup);
        joinGroup(2, numServersPerGroup);
        assertConfigBalanced();

        runState.addClientWorker(client(1),
                TransactionalKVStoreWorkload.builder().commandStrings(
                        "MULTIPUT:key%i#1:foo%i:key%i#2:foo%i")
                                            .resultStrings(OK)
                                            .numTimes(numRounds).build());
        runState.addClientWorker(client(2),
                TransactionalKVStoreWorkload.builder().commandStrings(
                        "MULTIGET:key%i#1:key%i#2").numTimes(numRounds)
                                            .build());

        runState.waitFor();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK).addInvariant(
                resultsHaveType(client(2), MultiGetResult.class))
                   .addInvariant(MULTI_GETS_MATCH);
        assertRunInvariantsHold();
    }

    private void repeatedPutsGetsInternal(boolean moveShards)
            throws InterruptedException {
        int numGroups = 3, numServersPerGroup = 3, numShardMasters = 3,
                numShards = 10, testLengthSecs = 50, nClients = 5;

        setupStates(numGroups, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        for (int g = 1; g <= numGroups; g++) {
            joinGroup(g, numServersPerGroup);
        }
        assertConfigBalanced();

        // Startup the clients
        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i), TransactionalKVStoreWorkload
                    .differentKeysInfiniteWorkload(numShards), false, true);
        }

        long startTime = System.currentTimeMillis();

        if (moveShards) {
            Thread t = moveShards(numGroups, numShards);
            t.start();
            startedThreads.add(t);
        }

        Thread.sleep(testLengthSecs * 1000);

        long finishTime = System.currentTimeMillis();

        // Shut everything down
        shutdownStartedThreads();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();
        assertMaxFinishTimeLessThan(4000, startTime, finishTime);
    }

    @Test(timeout = 60 * 1000)
    @PrettyTestName("Repeated MultiPuts and MultiGets, different keys")
    @Category(RunTests.class)
    @TestPointValue(20)
    public void test05RepeatedPutsGets() throws InterruptedException {
        repeatedPutsGetsInternal(false);
    }

    @Test(timeout = 60 * 1000)
    @PrettyTestName("Repeated MultiPuts and MultiGets, different keys")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(20)
    public void test06RepeatedPutsGetsUnreliable() throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        repeatedPutsGetsInternal(false);
    }

    @Test(timeout = 60 * 1000)
    @PrettyTestName(
            "Repeated MultiPuts and MultiGets, different keys; constant movement")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(20)
    public void test07ConstantMovement() throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        repeatedPutsGetsInternal(true);
    }

    @Test
    @PrettyTestName("Single client, single group; MultiPut, MultiGet")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test08SingleClientSingleGroupSearch() {
        setupStates(1, 1, 1, 10);
        initSearchState.addClientWorker(client(1),
                TransactionalKVStoreWorkload.putGetWorkload);
        singleClientSingleGroupSearch();
    }

    @Test
    @PrettyTestName("Single client, multi-group; MultiPut, MultiGet")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test09SingleClientMultiGroupSearch() {
        setupStates(2, 1, 1, 10);
        initSearchState.addClientWorker(client(1),
                TransactionalKVStoreWorkload.putGetWorkload);
        singleClientMultiGroupSearch();
    }

    @Test
    @PrettyTestName("Multi-client, multi-group; MultiPut, Swap, MultiGet")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test10MultiClientMultiGroupSearch() {
        setupStates(2, 1, 1, 2);

        Workload w1 = TransactionalKVStoreWorkload.builder().commands(
                multiPut("foo-1", "X", "foo-2", "Y"), swap("foo-1", "foo-2"))
                                                  .results(multiPutOk(),
                                                          swapOk()).build();
        initSearchState.addClientWorker(client(1), w1);

        Workload w2 = TransactionalKVStoreWorkload.builder().commands(
                multiGet("foo-1", "foo-2")).results(
                multiGetResult("foo-1", "Y", "foo-2", "X")).build();
        initSearchState.addClientWorker(client(2), w2);

        multiClientMultiGroupSearch();
    }
}
