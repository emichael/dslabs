package dslabs.paxos;

import com.google.common.collect.Lists;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.junit.BaseJUnitTest;
import dslabs.framework.testing.junit.PrettyTestName;
import dslabs.framework.testing.junit.RunTests;
import dslabs.framework.testing.junit.SearchTests;
import dslabs.framework.testing.junit.TestPointValue;
import dslabs.framework.testing.junit.UnreliableTests;
import dslabs.framework.testing.runner.RunState;
import dslabs.framework.testing.search.Search;
import dslabs.framework.testing.search.SearchResults;
import dslabs.framework.testing.search.SearchState;
import dslabs.kvstore.KVStore;
import dslabs.kvstore.KVStoreWorkload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import static dslabs.framework.testing.StatePredicate.ALL_RESULTS_SAME;
import static dslabs.framework.testing.StatePredicate.CLIENTS_DONE;
import static dslabs.framework.testing.StatePredicate.NONE_DECIDED;
import static dslabs.framework.testing.StatePredicate.RESULTS_OK;
import static dslabs.framework.testing.search.SearchResults.EndCondition.INVARIANT_VIOLATED;
import static dslabs.kvstore.KVStoreWorkload.APPENDS_LINEARIZABLE;
import static dslabs.kvstore.KVStoreWorkload.append;
import static dslabs.kvstore.KVStoreWorkload.appendDifferentKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.appendResult;
import static dslabs.kvstore.KVStoreWorkload.appendSameKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.differentKeysInfiniteWorkload;
import static dslabs.kvstore.KVStoreWorkload.get;
import static dslabs.kvstore.KVStoreWorkload.getResult;
import static dslabs.kvstore.KVStoreWorkload.put;
import static dslabs.kvstore.KVStoreWorkload.putGetWorkload;
import static dslabs.kvstore.KVStoreWorkload.putOk;
import static dslabs.kvstore.KVStoreWorkload.putWorkload;
import static dslabs.kvstore.KVStoreWorkload.simpleWorkload;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PaxosTest extends BaseJUnitTest {
    @Override
    public void setupTest() {
        super.setupTest();
        builder = StateGenerator.builder();
        builder.workloadSupplier(KVStoreWorkload.emptyWorkload());
    }

    static Address[] servers(int numServers) {
        final Address[] servers = new Address[numServers];
        for (int i = 0; i < numServers; i++) {
            servers[i] = server(i + 1);
        }
        return servers;
    }

    private static void setupBuilder(StateGeneratorBuilder builder,
                                     Address[] servers) {
        builder.serverSupplier(
                a -> new PaxosServer(a, servers.clone(), new KVStore()));
        builder.clientSupplier(a -> new PaxosClient(a, servers.clone()));
    }

    static StateGeneratorBuilder builder(Address[] servers) {
        StateGeneratorBuilder builder = StateGenerator.builder();
        builder.workloadSupplier(KVStoreWorkload.emptyWorkload());
        setupBuilder(builder, servers);
        return builder;
    }

    private void setupStates(int numServers) {
        setupBuilder(builder, servers(numServers));

        runState = new RunState(builder.build());
        initSearchState = new SearchState(builder.build());

        for (Address server : servers(numServers)) {
            runState.addServer(server);
            initSearchState.addServer(server);
        }
    }


    /* Tests */

    @Test(timeout = 2 * 1000, expected = InterruptedException.class)
    @PrettyTestName("Client throws InterruptedException")
    @TestPointValue(5)
    public void test01ThrowsException() throws InterruptedException {
        setupStates(3);
        final Thread mainThread = Thread.currentThread();
        Client client = runState.addClient(client(1));
        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }

            mainThread.interrupt();
        }, "Interrupter").start();
        client.sendCommand(get("foo"));
        // Should never return since the runState wasn't started
        client.getResult();
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Single client, simple operations")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test02Basic() throws InterruptedException {
        setupStates(3);
        runState.addClientWorker(client(1), simpleWorkload);

        runState.run(runSettings);

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Progress with no partition")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test03NoPartition() throws InterruptedException {
        setupStates(5);
        Client client1 = runState.addClient(client(1));
        Client client2 = runState.addClient(client(2));
        Client client3 = runState.addClient(client(3));

        runState.start(runSettings);

        sendCommandAndCheck(client1, put("foo", "bar"), putOk());
        sendCommandAndCheck(client2, put("foo", "baz"), putOk());
        sendCommandAndCheck(client3, get("foo"), getResult("baz"));
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Progress in majority")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test04ProgressInMajority() throws InterruptedException {
        setupStates(5);
        Client client = runState.addClient(client(1));

        runSettings.partition(server(1), server(2), server(3), client(1));
        runState.start(runSettings);

        sendCommandAndCheck(client, put("foo", "bar"), putOk());
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("No progress in minority")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test05NoProgressInMinority() throws InterruptedException {
        setupStates(5);
        Client client = runState.addClient(client(1));

        runSettings.waitForClients(false);
        runSettings.maxTimeSecs(2);
        runSettings.partition(server(1), server(2), client(1));

        client.sendCommand(put("foo", "bar"));

        runState.run(runSettings);

        assertFalse(client.hasResult());
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Progress after partition healed")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test06ProgressAfterHeal() throws InterruptedException {
        setupStates(5);
        Client client1 = runState.addClient(client(1));
        Client client2 = runState.addClient(client(2));

        runSettings.maxTimeSecs(2);
        runSettings.partition(server(1), server(2), client(1));

        client1.sendCommand(put("foo", "bar"));

        runState.run(runSettings);

        runSettings.maxTimeSecs(-1);
        runSettings.resetNetwork();

        runState.start(runSettings);
        assertEquals(putOk(), client1.getResult());

        sendCommandAndCheck(client2, get("foo"), getResult("bar"));
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("One server switches partitions")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test07ServerSwitchesPartitions() throws InterruptedException {
        setupStates(5);
        Client client1 = runState.addClient(client(1));
        Client client2 = runState.addClient(client(2));

        runSettings.partition(server(1), server(2), server(3), client(1));

        runState.start(runSettings);

        sendCommandAndCheck(client1, put("foo", "bar"), putOk());

        runState.stop();
        runSettings.resetNetwork();
        runSettings.partition(server(3), server(4), server(5), client(2));
        runState.start(runSettings);

        sendCommandAndCheck(client2, get("foo"), getResult("bar"));
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Multiple clients, synchronous put/get")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test08SynchronousClients() throws InterruptedException {
        int nIters = 20, nClients = 15;

        builder.workloadSupplier(
                KVStoreWorkload.builder().commandStrings().build());

        setupStates(3);
        for (int i = 0; i < nClients; i++) {
            runState.addClientWorker(client(i));
        }

        runState.start(runSettings);

        for (int i = 0; i < nIters; i++) {
            runState.addCommand("PUT:foo:%r8");
            runState.waitFor();

            runState.addCommand("GET:foo");
            runState.waitFor();
        }

        runState.stop();

        runSettings.addInvariant(ALL_RESULTS_SAME);
        assertRunInvariantsHold();
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Multiple clients, concurrent appends")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test09ConcurrentAppends() throws InterruptedException {
        setupStates(3);
        int nClients = 25, nRounds = 5;

        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i), appendSameKeyWorkload(nRounds));
        }

        runState.run(runSettings);

        runSettings.addInvariant(CLIENTS_DONE);
        runSettings.addInvariant(APPENDS_LINEARIZABLE);
        assertRunInvariantsHold();
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Message count")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test10MessageCount() throws InterruptedException {
        int nRounds = 500, nServers = 5;

        setupStates(5);
        runState.addClientWorker(client(1), appendSameKeyWorkload(nRounds));

        runState.run(runSettings);

        int totalServerMessages = 0;
        for (Address s : runState.serverAddresses()) {
            totalServerMessages += runState.network().numMessagesSentTo(s);
        }

        double messagesPerAgreement = ((double) totalServerMessages) / nRounds;
        int allowed = 15 * nServers;

        if (messagesPerAgreement > allowed) {
            fail("Too many messages sent, " + allowed +
                    " per command allowed, got " + messagesPerAgreement);
        }
    }

    @Test(timeout = 20 * 1000)
    @PrettyTestName("Old commands garbage collected")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test11ClearsMemory() throws InterruptedException {
        int valueSize = 1000000, items = 10, iters = 2;

        setupStates(3);
        Client client = runState.addClient(client(1));
        runSettings.partition(server(2), server(3), client(1));

        // Test initial allocated space
        long initialBytes = nodesSize();
        System.out
                .println("Using " + readableSize(initialBytes) + " at start.");
        // Must use less than 2MB at first
        assertTrue(initialBytes < 2 * Math.pow(1024, 2));

        // Now, add a bunch of large items
        runState.start(runSettings);

        for (int i = 0; i < iters; i++) {
            for (int key = 0; key < items; key++) {
                sendCommandAndCheck(client,
                        put(key, RandomStringUtils.randomAscii(valueSize)),
                        putOk());
            }
        }

        runState.stop();

        long afterPutBytes = nodesSize();
        System.out.println(
                "Using " + readableSize(afterPutBytes) + " after puts.");
        // Must at least have random values in memory at nodes (~27 MB)
        assertTrue(afterPutBytes > valueSize * items * 3);

        // Clear memory and let nodes talk to each other
        runSettings.resetNetwork();
        runState.start(runSettings);
        for (int i = 0; i < 2; i++) {
            for (int key = 0; key < items; key++) {
                sendCommandAndCheck(client, put(key, "foo"), putOk());
            }
        }
        Thread.sleep(4000);
        runState.stop();

        long finishBytes = nodesSize();
        System.out.println("Using " + readableSize(finishBytes) + " at end.");
        // Must be back under 2MB at end
        assertTrue(finishBytes < 2 * Math.pow(1024, 2));
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Single client, simple operations")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(10)
    public void test12BasicUnreliable() throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        test02Basic();
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Two sequential clients")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(10)
    public void test13SimplePutGetUnreliable() throws InterruptedException {
        setupStates(3);
        Client client1 = runState.addClient(client(1));
        Client client2 = runState.addClient(client(2));
        runSettings.networkDeliverRate(0.8);
        runState.start(runSettings);

        sendCommandAndCheck(client1, put("foo", "bar"), putOk());
        sendCommandAndCheck(client2, get("foo"), getResult("bar"));
    }

    @Test(timeout = 30 * 1000)
    @PrettyTestName("Multiple clients, synchronous put/get")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(15)
    public void test14SynchronousClientsUnreliable()
            throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        test08SynchronousClients();
    }

    @Test(timeout = 20 * 1000)
    @PrettyTestName("Multiple clients, concurrent appends")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(15)
    public void test15ConcurrentAppendsUnreliable()
            throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        test09ConcurrentAppends();
    }

    @Test(timeout = 20 * 1000)
    @PrettyTestName("Tolerates holes in Paxos log")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test16ToleratesHoles() throws InterruptedException {
        final int nClients = 5, nServers = 5;

        setupStates(nServers);

        runSettings.addInvariant(RESULTS_OK);
        runState.start(runSettings);

        // Startup the clients
        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i), differentKeysInfiniteWorkload,
                    false, true);
        }

        long startTime = System.currentTimeMillis();

        Thread.sleep(5000);
        assertRunInvariantsHold();

        // Partition off some servers and the clients
        List<Address> partition =
                Lists.newArrayList(server(1), server(2), server(3));
        for (int i = 1; i <= nClients; i++) {
            partition.add(client(i));
        }
        runSettings.partition(partition);
        Thread.sleep(1000);
        assertRunInvariantsHold();

        // Heal the partition
        runSettings.reconnect();
        Thread.sleep(5000);

        long finishTime = System.currentTimeMillis();

        // Shut the clients down
        runState.stop();

        assertRunInvariantsHold();
        assertMaxFinishTimeLessThan(3000, startTime, finishTime);
    }

    @Test(timeout = 35 * 1000)
    @PrettyTestName("Constant repartitioning, check maximum wait time")
    @Category(RunTests.class)
    @TestPointValue(20)
    public void test17ConstantRepartition() throws InterruptedException {
        final int nClients = 5, nServers = 5, testLengthSecs = 30;

        // Startup the clients with 10ms inter-request delay
        setupStates(nServers);
        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i),
                    differentKeysInfiniteWorkload(10), false, true);
        }

        long startTime = System.currentTimeMillis();

        // Re-partition -> 2s -> re-partition -> 2s -> heal -> 2s
        Thread partition = new Thread(() -> {
            List<Address> clients = new ArrayList<>();
            for (int i = 1; i <= nClients; i++) {
                clients.add(client(i));
            }

            List<Address> servers = new ArrayList<>();
            for (Address server : runState.serverAddresses()) {
                servers.add(server);
            }

            try {
                while (!Thread.interrupted()) {
                    for (int i = 0; i < 2; i++) {
                        List<Address> newPartition = new LinkedList<>();
                        newPartition.addAll(clients);
                        Collections.shuffle(servers);

                        // Grab a majority
                        for (int j = 0; j * 2 <= nServers; j++) {
                            newPartition.add(servers.get(j));
                        }

                        runSettings.reconnect().partition(newPartition);
                        Thread.sleep(2000);
                    }

                    runSettings.reconnect();
                    Thread.sleep(2000);
                }
            } catch (InterruptedException ignored) {
            }
        }, "Repartition system");
        startedThreads.add(partition);
        partition.start();

        // Let the clients run
        runState.start(runSettings);
        Thread.sleep(testLengthSecs * 1000);

        // Shut the clients down
        long endTime = System.currentTimeMillis();
        shutdownStartedThreads();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();

        // Make sure maximum wait is below 2s (should be much less)
        assertMaxFinishTimeLessThan(2000, startTime, endTime);
    }

    @Test(timeout = 35 * 1000)
    @PrettyTestName("Constant repartitioning, check maximum wait time")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(30)
    public void test18ConstantRepartitionUnreliable()
            throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        test17ConstantRepartition();
    }

    @Test(timeout = 70 * 1000)
    @PrettyTestName("Constant repartitioning, full throughput")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(30)
    public void test19RepartitionFullThroughput() throws InterruptedException {
        final int nClients = 2, nServers = 5, testLengthSecs = 50, nRounds = 10;

        runSettings.networkDeliverRate(0.8);

        // Setup servers and clients
        setupStates(nServers);
        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i), differentKeysInfiniteWorkload,
                    false, false);
        }

        // Re-partition -> 5s -> re-partition -> 1s -> heal -> 5s
        Thread partition = new Thread(() -> {
            List<Address> clients = new ArrayList<>();
            for (int i = 1; i <= nClients; i++) {
                clients.add(client(i));
            }

            List<Address> servers = new ArrayList<>();
            for (Address server : runState.serverAddresses()) {
                servers.add(server);
            }

            try {
                while (!Thread.interrupted()) {
                    for (int i = 0; i < 2; i++) {
                        List<Address> newPartition = new LinkedList<>();
                        newPartition.addAll(clients);
                        Collections.shuffle(servers);

                        // Grab a majority
                        for (int j = 0; j * 2 <= nServers; j++) {
                            newPartition.add(servers.get(j));
                        }

                        runSettings.reconnect().partition(newPartition);

                        if (i == 0) {
                            Thread.sleep(5000);
                        } else {
                            Thread.sleep(1000);
                        }
                    }

                    runSettings.reconnect();
                    Thread.sleep(5000);
                }
            } catch (InterruptedException ignored) {
            }
        }, "Repartition system");
        startedThreads.add(partition);
        partition.start();

        // Let the clients run
        runState.start(runSettings);
        Thread.sleep(testLengthSecs * 1000);

        // Shut the clients down
        shutdownStartedThreads();
        runState.stop();

        // Make sure that all clients got the correct results
        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();

        // Kill the old clients and add a new batch
        for (int i = 1; i <= nClients; i++) {
            runState.removeNode(client(i));
            runState.addClientWorker(client(i + nClients),
                    appendDifferentKeyWorkload(nRounds));
        }

        // Run the new batch of clients to make sure we're not in deadlock
        runSettings.reconnect();
        runState.run(runSettings);
        assertRunInvariantsHold();
    }


    @Test
    @PrettyTestName("Single client, simple operations")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test20basicSearch() {
        setupStates(3);
        initSearchState.addClientWorker(client(1), putGetWorkload);

        // First, check that Paxos can execute a single command
        searchSettings.maxTimeSecs(15)
                      .partition(server(1), server(2), client(1))
                      .addInvariant(NONE_DECIDED);
        SearchResults results = Search.bfs(initSearchState, searchSettings);
        assertEndCondition(INVARIANT_VIOLATED, results);

        // From there, make sure the second command can be executed
        searchSettings.resetNetwork().clearInvariants()
                      .addInvariant(CLIENTS_DONE.negate());
        assertEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(results.invariantViolatingState(), searchSettings));

        // Check that linearizability is preserved (with and without timeouts)
        searchSettings.clearInvariants().addInvariant(RESULTS_OK)
                      .addPrune(CLIENTS_DONE).maxTimeSecs(30);
        assertNotEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(results.invariantViolatingState(), searchSettings));

        searchSettings.deliverTimeouts(false);
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(results.invariantViolatingState(), searchSettings));
    }

    @Test
    @PrettyTestName("Single client, no progress in minority")
    @Category(SearchTests.class)
    @TestPointValue(15)
    public void test21NoProgressInMinoritySearch() {
        setupStates(5);

        initSearchState.addClientWorker(client(1), putWorkload);

        // Check that no commands can be decided without a majority
        searchSettings.maxTimeSecs(30).addInvariant(NONE_DECIDED)
                      .partition(server(1), server(2), client(1));
        assertNotEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(initSearchState, searchSettings));

        searchSettings.deliverTimeouts(false);
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(initSearchState, searchSettings));

    }

    @Test
    @PrettyTestName("Two clients, sequential appends visible")
    @Category(SearchTests.class)
    @TestPointValue(30)
    public void test22TwoClientsSearch() {
        setupStates(3);

        initSearchState.addClientWorker(client(1),
                Workload.builder().commands(append("foo", "X"))
                        .results(appendResult("X")).build());
        initSearchState.addClientWorker(client(2),
                Workload.builder().commands(append("foo", "Y"))
                        .results(appendResult("XY")).build());

        // Send first append to one partition
        searchSettings.maxTimeSecs(30).addInvariant(NONE_DECIDED)
                      .partition(server(1), server(2), client(1));
        SearchResults results = Search.bfs(initSearchState, searchSettings);
        assertEndCondition(INVARIANT_VIOLATED, results);

        SearchState firstAppendSent = results.invariantViolatingState();

        // Check that second append can happen in both other partitions
        searchSettings.clearInvariants().addInvariant(CLIENTS_DONE.negate())
                      .resetNetwork()
                      .partition(server(1), server(3), client(2));
        assertEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        searchSettings.resetNetwork()
                      .partition(server(2), server(3), client(2));
        assertEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        // Checking that linearizability is preserved in both other partitions
        searchSettings.clearInvariants().addInvariant(RESULTS_OK)
                      .addPrune(CLIENTS_DONE).resetNetwork()
                      .partition(server(1), server(3), client(2));
        assertNotEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        searchSettings.resetNetwork()
                      .partition(server(2), server(3), client(2));
        assertNotEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        // Same checks but without timeouts (not necessarily useful)
        searchSettings.deliverTimeouts(false).resetNetwork()
                      .partition(server(1), server(3), client(2));
        assertNotEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        searchSettings.resetNetwork()
                      .partition(server(2), server(3), client(2));
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));
    }
}
