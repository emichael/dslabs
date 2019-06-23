package dslabs.paxos;

import com.google.common.collect.Lists;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.testing.AbstractState;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.StatePredicate;
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
import java.util.Objects;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
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
import static dslabs.paxos.PaxosLogSlotStatus.ACCEPTED;
import static dslabs.paxos.PaxosLogSlotStatus.CHOSEN;
import static dslabs.paxos.PaxosLogSlotStatus.CLEARED;
import static dslabs.paxos.PaxosLogSlotStatus.EMPTY;
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

    /* Predicates */

    private static PaxosLogSlotStatus status(Address a, int i,
                                             AbstractState s) {
        PaxosServer p = (PaxosServer) s.server(a);
        return p.status(i);
    }

    private static Command command(Address a, int i, AbstractState s) {
        PaxosServer p = (PaxosServer) s.server(a);
        return p.command(i);
    }

    private static StatePredicate hasStatus(Address a, int i,
                                            PaxosLogSlotStatus s) {
        return StatePredicate.statePredicate(
                String.format("%s has status %s in slot %s", a, s, i),
                st -> status(a, i, st) == s);
    }

    private static StatePredicate hasCommand(Address a, int i, Command c) {
        return StatePredicate.statePredicate(
                String.format("%s has command %s in slot %s", a, c, i),
                st -> Objects.equals(command(a, i, st), c));
    }

    /**
     * Checks whether a single slot in the log is consistent. More specifically,
     * it checks the following:
     *
     * 1) No two different commands are chosen.
     *
     * 2) If a command has been chosen, there is a majority of servers where the
     * command has been chosen or accepted or the slot has been cleared.
     *
     * 3) If the slot has been cleared, there is a majority of servers where the
     * slot is non-empty.
     *
     * This predicate is not completely exhaustive. In particular, the third
     * check above should not count acceptances of different commands towards
     * the same majority.
     */
    private static StatePredicate slotValid(int i) {
        return StatePredicate
                .statePredicateWithMessage("Logs consistent for slot " + i,
                        st -> {
                            Command chosen = null;
                            boolean isChosen = false, isCleared = false;

                            for (Address a : st.serverAddresses()) {
                                PaxosLogSlotStatus s = status(a, i, st);

                                if (s == CLEARED) {
                                    isCleared = true;
                                }

                                if (s == CHOSEN) {
                                    Command c = command(a, i, st);
                                    if (isChosen &&
                                            !Objects.equals(chosen, c)) {
                                        // There are two different commands chosen in the same slot
                                        return new ImmutablePair<>(false,
                                                String.format(
                                                        "Two different commands (%s and %s) chosen for slot %s",
                                                        chosen, c, i));
                                    }
                                    chosen = c;
                                    isChosen = true;
                                }
                            }

                            if (!isChosen && !isCleared) {
                                return new ImmutablePair<>(true, "");
                            }

                            int count = 0;
                            for (Address a : st.serverAddresses()) {
                                PaxosLogSlotStatus s = status(a, i, st);
                                if (s != EMPTY && (s != ACCEPTED || !isChosen ||
                                        Objects.equals(chosen,
                                                command(a, i, st)))) {
                                    count++;
                                }
                            }

                            if (2 * count <= st.numServers()) {
                                if (isChosen) {
                                    return new ImmutablePair<>(false,
                                            String.format(
                                                    "Command chosen for slot %s without a majority accepting",
                                                    i));
                                }

                                return new ImmutablePair<>(false, String.format(
                                        "Slot %s cleared without a majority accepting",
                                        i));

                            }

                            return new ImmutablePair<>(true, "");
                        });
    }

    /**
     * Checks that all of the non-empty and non-cleared log slots are valid.
     * Also performs some checks on the first non-cleared and last non-empty
     * methods.
     */
    private static StatePredicate LOGS_CONSISTENT =
            StatePredicate.statePredicateWithMessage("Logs consistent", st -> {
                int minNonCleared = Integer.MAX_VALUE, maxNonEmpty = 0;

                for (Address a : st.serverAddresses()) {
                    PaxosServer p = (PaxosServer) st.server(a);

                    int nc = p.firstNonCleared(), ne = p.lastNonEmpty();

                    minNonCleared =
                            Math.min(minNonCleared, p.firstNonCleared());
                    maxNonEmpty = Math.max(maxNonEmpty, p.lastNonEmpty());

                    if (nc < 1) {
                        return new ImmutablePair<>(false, String.format(
                                "%s returned %s as first non-cleared slot", a,
                                nc));
                    }

                    if (ne < 0) {
                        return new ImmutablePair<>(false, String.format(
                                "%s returned %s as last non-empty slot", a,
                                ne));
                    }

                    if (p.status(nc) == CLEARED) {
                        return new ImmutablePair<>(false, String.format(
                                "%s returned %s as first non-cleared slot, but slot has status cleared",
                                a, nc));
                    }

                    if (ne > 0 && p.status(ne) == EMPTY) {
                        return new ImmutablePair<>(false, String.format(
                                "%s returned %s as last non-empty slot, but slot has status empty",
                                a, ne));
                    }

                    if (nc > 1 && p.status(nc - 1) != CLEARED) {
                        return new ImmutablePair<>(false, String.format(
                                "%s returned %s as first non-cleared slot, but the previous slot isn't cleared",
                                a, nc));
                    }

                    if (p.status(ne + 1) != EMPTY) {
                        return new ImmutablePair<>(false, String.format(
                                "%s returned %s as last non-empty slot, but the next slot isn't empty",
                                a, ne));
                    }
                }

                for (int i = minNonCleared; i <= maxNonEmpty; i++) {
                    StatePredicate sv = slotValid(i);
                    if (!sv.test(st)) {
                        return new ImmutablePair<>(false, sv.detail(st));
                    }
                }

                return new ImmutablePair<>(true, "");
            });

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
        assertTrue(afterPutBytes > valueSize * items * 2);

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
                    false);
        }

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

        // Shut the clients down
        runState.stop();

        assertRunInvariantsHold();
        assertMaxWaitTimeLessThan(3000);
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
                    differentKeysInfiniteWorkload(10), false);
        }

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
        shutdownStartedThreads();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();

        // Make sure maximum wait is below 2s (should be much less)
        assertMaxWaitTimeLessThan(2000);
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
                    false);
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

        // Check that linearizability is preserved (with and without timers)
        searchSettings.clearInvariants().addInvariant(RESULTS_OK)
                      .addInvariant(LOGS_CONSISTENT).addPrune(CLIENTS_DONE)
                      .maxTimeSecs(30);
        assertNotEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(results.invariantViolatingState(), searchSettings));

        searchSettings.deliverTimers(false);
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

        searchSettings.deliverTimers(false);
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
                      .addInvariant(LOGS_CONSISTENT).addPrune(CLIENTS_DONE)
                      .resetNetwork()
                      .partition(server(1), server(3), client(2));
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        searchSettings.resetNetwork()
                      .partition(server(2), server(3), client(2));
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        // Same checks but without timers (not necessarily useful)
        searchSettings.deliverTimers(false).resetNetwork()
                      .partition(server(1), server(3), client(2));
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));

        searchSettings.resetNetwork()
                      .partition(server(2), server(3), client(2));
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(firstAppendSent, searchSettings));
    }

    @Test
    @PrettyTestName("Two clients, five servers, multiple leader changes")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test23QuorumCheckingSearch() {
        setupStates(5);

        Command c1 = append("foo", "X");
        Command c2 = append("foo", "Y");

        initSearchState.addClientWorker(client(1),
                Workload.builder().commands(c1).build());
        initSearchState.addClientWorker(client(2),
                Workload.builder().commands(c2).build());

        searchSettings.maxTimeSecs(30);

        // Nothing ever cleared, nothing in slot 2
        for (Address a : servers(5)) {
            searchSettings.addPrune(hasStatus(a, 2, EMPTY).negate());
            searchSettings.addPrune(hasStatus(a, 1, CLEARED));
        }

        // First two servers don't accept anything for now
        searchSettings.addPrune(hasStatus(server(1), 1, EMPTY).negate())
                      .addPrune(hasStatus(server(2), 1, EMPTY).negate());

        // Client 1 can talk to server 4; client 2 can talk to server 5
        searchSettings.nodeActive(client(1), false)
                      .linkActive(client(1), server(4), true)
                      .nodeActive(client(2), false)
                      .linkActive(client(2), server(5), true)
                      .addPrune(hasCommand(server(4), 1, c2))
                      .addPrune(hasCommand(server(5), 1, c1));

        // Find a state where server 3 gets client 1's command
        searchSettings.nodeActive(server(1), false).nodeActive(server(5), false)
                      .deliverTimers(server(1), false)
                      .deliverTimers(server(5), false)
                      .deliverTimers(client(2), false)
                      .addInvariant(hasCommand(server(4), 1, c1).negate());
        SearchResults results = Search.bfs(initSearchState, searchSettings);
        assertEndCondition(INVARIANT_VIOLATED, results);
        SearchState c1AtServer4 = results.invariantViolatingState();
        searchSettings.clearInvariants()
                      .addInvariant(hasCommand(server(3), 1, c1).negate());
        results = Search.bfs(c1AtServer4, searchSettings);
        assertEndCondition(INVARIANT_VIOLATED, results);
        SearchState c1AtServer3 = results.invariantViolatingState();

        // Now, find a state where server 3 has client 2's command
        searchSettings.nodeActive(server(4), false).nodeActive(server(3), false)
                      .nodeActive(server(1), true).nodeActive(server(5), true)
                      .clearDeliverTimers().deliverTimers(server(4), false)
                      .deliverTimers(server(3), false)
                      .deliverTimers(client(1), false).clearInvariants()
                      .addInvariant(hasCommand(server(5), 1, c2).negate());
        results = Search.bfs(c1AtServer3, searchSettings);
        assertEndCondition(INVARIANT_VIOLATED, results);
        SearchState c2AtServer5 = results.invariantViolatingState();
        searchSettings.nodeActive(server(3), true)
                      .deliverTimers(server(3), true).clearInvariants()
                      .addInvariant(hasCommand(server(3), 1, c2).negate());
        results = Search.bfs(c2AtServer5, searchSettings);
        assertEndCondition(INVARIANT_VIOLATED, results);
        SearchState c2AtServer3 = results.invariantViolatingState();

        // Now, clear the prunes and find a state where server 2 has c1
        searchSettings.clear().maxTimeSecs(30).maxDepth(1000);

        // Drop all pending messages to narrow search
        c2AtServer3.dropPendingMessages();

        for (Address a : servers(5)) {
            searchSettings.addPrune(hasStatus(a, 1, CLEARED));
        }
        searchSettings.addPrune(hasCommand(server(4), 1, c2))
                      .addPrune(hasCommand(server(2), 1, c2))
                      .addPrune(hasCommand(server(1), 1, c2))
                      .nodeActive(server(5), false).nodeActive(server(3), false)
                      .nodeActive(client(2), false)
                      .linkActive(server(1), server(2), false)
                      .linkActive(server(2), server(1), false)
                      .deliverTimers(server(5), false)
                      .deliverTimers(server(3), false)
                      .deliverTimers(client(2), false)
                      .addInvariant(hasCommand(server(1), 1, c1).negate());
        results = Search.bfs(c2AtServer3, searchSettings);
        assertEndCondition(INVARIANT_VIOLATED, results);
        SearchState c1AtServer1 = results.invariantViolatingState();

        // Make sure server 4 can get c1 chosen
        searchSettings.clearInvariants()
                      .addInvariant(hasStatus(server(4), 1, CHOSEN).negate());
        assertEndConditionAndContinue(INVARIANT_VIOLATED,
                Search.bfs(c1AtServer1, searchSettings));

        // Re-add ignored messages
        c1AtServer1.undropMessagesFrom(server(3));

        searchSettings.linkActive(server(3), server(4), true).clearInvariants()
                      .addInvariant(slotValid(1));
        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.bfs(c1AtServer1, searchSettings));
    }

    private void randomSearch() {
        initSearchState.addClientWorker(client(1),
                KVStoreWorkload.builder().commands(append("foo", "x")).build());
        initSearchState.addClientWorker(client(2),
                KVStoreWorkload.builder().commands(append("foo", "y")).build());

        searchSettings.maxDepth(1000).maxTimeSecs(20)
                      .addInvariant(APPENDS_LINEARIZABLE)
                      .addInvariant(LOGS_CONSISTENT).addPrune(CLIENTS_DONE);

        assertNotEndCondition(INVARIANT_VIOLATED,
                Search.dfs(initSearchState, searchSettings));
    }

    @Test
    @PrettyTestName("Three server random search")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test24ThreeServerRandomSearch() {
        setupStates(3);
        randomSearch();
    }

    @Test
    @PrettyTestName("Five server random search")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test25FiveServerRandomSearch() {
        setupStates(5);
        randomSearch();
    }
}
