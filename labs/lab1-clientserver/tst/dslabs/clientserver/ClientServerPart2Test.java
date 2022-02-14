package dslabs.clientserver;

import dslabs.framework.testing.junit.PrettyTestName;
import dslabs.framework.testing.junit.RunTests;
import dslabs.framework.testing.junit.SearchTests;
import dslabs.framework.testing.junit.TestPointValue;
import dslabs.framework.testing.junit.UnreliableTests;
import dslabs.kvstore.KVStoreWorkload;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import static dslabs.framework.testing.StatePredicate.CLIENTS_DONE;
import static dslabs.framework.testing.StatePredicate.NONE_DECIDED;
import static dslabs.framework.testing.StatePredicate.RESULTS_OK;
import static dslabs.kvstore.KVStoreWorkload.APPENDS_LINEARIZABLE;
import static dslabs.kvstore.KVStoreWorkload.append;
import static dslabs.kvstore.KVStoreWorkload.appendAppendGet;
import static dslabs.kvstore.KVStoreWorkload.appendDifferentKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.appendResult;
import static dslabs.kvstore.KVStoreWorkload.appendSameKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.differentKeysInfiniteWorkload;
import static dslabs.kvstore.KVStoreWorkload.put;
import static dslabs.kvstore.KVStoreWorkload.putAppendGetWorkload;
import static dslabs.kvstore.KVStoreWorkload.putOk;
import static dslabs.kvstore.KVStoreWorkload.simpleWorkload;
import static org.junit.Assert.assertTrue;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class ClientServerPart2Test extends ClientServerBaseTest {

    @Test(timeout = 15 * 1000)
    @PrettyTestName("Single client basic operations")
    @TestPointValue(20)
    @Category({RunTests.class, UnreliableTests.class})
    public void test01UnreliableClient() throws InterruptedException {
        runSettings.networkUnreliable(true);

        runState.addClientWorker(client(1), simpleWorkload);

        runSettings.addInvariant(RESULTS_OK);
        runState.run(runSettings);
    }

    @Test(timeout = 15 * 1000)
    @PrettyTestName("Single client sequential appends")
    @TestPointValue(20)
    @Category({RunTests.class, UnreliableTests.class})
    public void test02SingleClientAppendsUnreliable()
            throws InterruptedException {
        final int numRounds = 50;
        runSettings.networkDeliverRate(0.8);

        runState.addClientWorker(client(1),
                appendDifferentKeyWorkload(numRounds));

        runSettings.addInvariant(RESULTS_OK);
        runState.run(runSettings);
    }

    @Test(timeout = 30 * 1000)
    @PrettyTestName("Multi-client different key appends")
    @TestPointValue(20)
    @Category({RunTests.class, UnreliableTests.class})
    public void test03MultiClientDifferentKeyUnreliable()
            throws InterruptedException {
        final int numRounds = 100, numClients = 10;
        runSettings.networkDeliverRate(0.8);

        for (int i = 1; i <= numClients; i++) {
            runState.addClientWorker(client(i),
                    appendDifferentKeyWorkload(numRounds));
        }

        runSettings.addInvariant(RESULTS_OK);
        runState.run(runSettings);
    }

    @Test(timeout = 15 * 1000)
    @PrettyTestName("Multi-client same key appends")
    @TestPointValue(20)
    @Category({RunTests.class, UnreliableTests.class})
    public void test04MultiClientSameKeyUnreliable()
            throws InterruptedException {
        final int numRounds = 5, numClients = 10;
        runSettings.networkDeliverRate(0.8);

        for (int i = 1; i <= numClients; i++) {
            runState.addClientWorker(client(i),
                    appendSameKeyWorkload(numRounds));
        }

        runSettings.addInvariant(APPENDS_LINEARIZABLE);
        runState.run(runSettings);
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Old commands garbage collected")
    @TestPointValue(20)
    @Category({RunTests.class})
    public void test05GarbageCollection() throws InterruptedException {
        final int valueSize = 1000000, items = 5, iters = 3, numClients = 5;

        for (int c = 1; c <= numClients; c++) {
            runState.addClient(client(c));
        }

        // Test initial allocated space
        long initialBytes = nodesSize();
        System.out
                .println("Using " + readableSize(initialBytes) + " at start.");
        // Must use less than 2MB at first
        assertTrue(initialBytes < 2 * Math.pow(1024, 2));

        // Now, add a bunch of large items
        runState.start(runSettings);
        Map<String, String> kv = new HashMap<>();
        for (int i = 0; i < iters; i++) {
            for (int key = 0; key < items; key++) {
                for (int c = 1; c <= numClients; c++) {
                    String k = String.format("client%s-key%s", c, key);
                    String v = RandomStringUtils.randomAscii(valueSize);
                    String nv = kv.getOrDefault(k, "") + v;
                    sendCommandAndCheck(runState.client(client(c)),
                            append(k, v), appendResult(nv));
                    kv.put(k, nv);
                }
            }
        }
        runState.stop();

        long afterAppendBytes = nodesSize();
        System.out.println(
                "Using " + readableSize(afterAppendBytes) + " after appends.");
        // Must at least have random values in memory at nodes (~34 MB)
        assertTrue(afterAppendBytes > valueSize * items * numClients);

        // Clear memory
        runSettings.resetNetwork();
        runState.start(runSettings);
        for (int key = 0; key < items; key++) {
            for (int c = 1; c <= numClients; c++) {
                String k = String.format("client%s-key%s", c, key);
                sendCommandAndCheck(runState.client(client(c)), put(k, ""),
                        putOk());
            }
        }
        runState.stop();

        long finishBytes = nodesSize();
        System.out.println("Using " + readableSize(finishBytes) + " at end.");

        // Must be back under 2MB at end
        assertTrue(finishBytes < 2 * Math.pow(1024, 2));
    }

    @Test(timeout = 40 * 1000)
    @PrettyTestName("Long-running workload")
    @TestPointValue(20)
    @Category({RunTests.class})
    public void test06LongRunningWorkload() throws InterruptedException {
        final int numClients = 4, testLengthSecs = 30;

        for (int i = 1; i <= numClients; i++) {
            runState.addClientWorker(client(i), differentKeysInfiniteWorkload,
                    false);
        }

        // Let the clients run
        runSettings.maxTimeSecs(testLengthSecs);
        runState.run(runSettings);

        // Check if all the results were right
        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();

        // No client should wait more than 1 second (it should be much less)
        assertMaxWaitTimeLessThan(1000);
    }

    @Test
    @PrettyTestName("Single client; Put, Append, Get")
    @TestPointValue(20)
    @Category(SearchTests.class)
    public void test07SingleClientSearch() {
        initSearchState.addClientWorker(client(1), putAppendGetWorkload);

        System.out.println("Checking that an end state is reachable");
        searchSettings.addInvariant(RESULTS_OK).addGoal(CLIENTS_DONE)
                      .maxTimeSecs(10);
        bfs(initSearchState);
        assertGoalFound();

        System.out.println("Checking that all reachable states are good");
        searchSettings.clearGoals().addPrune(CLIENTS_DONE);
        bfs(initSearchState);
        assertSpaceExhausted();

        System.out.println(
                "Checking that there is no progress if client and server " +
                        "cannot communicate");
        searchSettings.addInvariant(NONE_DECIDED).networkActive(false)
                      .maxTimeSecs(5);
        bfs(initSearchState);
        assertSpaceExhausted();
    }

    @Test
    @PrettyTestName("Single client; Append, Append, Get")
    @TestPointValue(20)
    @Category(SearchTests.class)
    public void test08SingleClientAppendSearch() {
        initSearchState.addClientWorker(client(1), appendAppendGet);

        System.out.println("Checking that an end state is reachable");
        searchSettings.addInvariant(RESULTS_OK).addGoal(CLIENTS_DONE)
                      .maxTimeSecs(10);
        bfs(initSearchState);
        assertGoalFound();

        System.out.println("Checking that all reachable states are good");
        searchSettings.clearGoals().addPrune(CLIENTS_DONE);
        bfs(initSearchState);
        assertSpaceExhausted();
    }

    @Test
    @PrettyTestName("Multi-client different keys")
    @TestPointValue(20)
    @Category(SearchTests.class)
    public void test09MultiClientDifferentKeySearch() {
        final int numClients = 2, numRounds = 3;

        for (int i = 1; i <= numClients; i++) {
            initSearchState.addClientWorker(client(i),
                    appendDifferentKeyWorkload(numRounds));
        }

        System.out.println("Checking that an end state is reachable");
        searchSettings.addInvariant(RESULTS_OK).addGoal(CLIENTS_DONE)
                      .maxTimeSecs(30);
        bfs(initSearchState);
        assertGoalFound();

        System.out.println("Checking that all reachable states are good");
        searchSettings.clearGoals().addPrune(CLIENTS_DONE);
        bfs(initSearchState);
        assertSpaceExhausted();
    }

    @Test
    @PrettyTestName("Multi-client same key")
    @TestPointValue(20)
    @Category(SearchTests.class)
    public void test10MultiClientSameKeySearch() {
        final int numClients = 2, numRounds = 3;

        // Now, let's have clients modify the same keys
        for (int i = 1; i <= numClients; i++) {
            initSearchState.addClientWorker(client(i),
                    KVStoreWorkload.builder().commandStrings("APPEND:foo:%i")
                                   .numTimes(numRounds).build());
        }

        System.out.println("Checking that an end state is reachable");
        searchSettings.addInvariant(APPENDS_LINEARIZABLE).addGoal(CLIENTS_DONE)
                      .maxTimeSecs(30);
        bfs(initSearchState);
        assertGoalFound();

        System.out.println("Checking that all reachable states are good");
        searchSettings.clearGoals().addPrune(CLIENTS_DONE);
        bfs(initSearchState);
        assertSpaceExhausted();
    }

    @Test
    @PrettyTestName("Infinite workload searches")
    @TestPointValue(20)
    @Category(SearchTests.class)
    public void test11RandomSearchInfiniteWorkloads() {
        initSearchState
                .addClientWorker(client(1), differentKeysInfiniteWorkload);

        System.out.println("Checking that all reachable states are good");
        searchSettings.maxTimeSecs(15).addInvariant(RESULTS_OK);
        bfs(initSearchState);

        searchSettings.maxDepth(1000);
        dfs(initSearchState);

        initSearchState
                .addClientWorker(client(2), differentKeysInfiniteWorkload);
        dfs(initSearchState);
    }
}
