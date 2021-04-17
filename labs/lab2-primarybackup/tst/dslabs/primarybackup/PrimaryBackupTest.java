package dslabs.primarybackup;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.MessageEnvelope;
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
import dslabs.framework.testing.runner.RunSettings;
import dslabs.framework.testing.runner.RunState;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import dslabs.kvstore.KVStore;
import dslabs.kvstore.KVStoreWorkload;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import static dslabs.framework.testing.StatePredicate.ALL_RESULTS_SAME;
import static dslabs.framework.testing.StatePredicate.CLIENTS_DONE;
import static dslabs.framework.testing.StatePredicate.RESULTS_OK;
import static dslabs.framework.testing.StatePredicate.clientDone;
import static dslabs.framework.testing.StatePredicate.statePredicate;
import static dslabs.kvstore.KVStoreWorkload.APPENDS_LINEARIZABLE;
import static dslabs.kvstore.KVStoreWorkload.append;
import static dslabs.kvstore.KVStoreWorkload.appendDifferentKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.appendResult;
import static dslabs.kvstore.KVStoreWorkload.appendSameKeyWorkload;
import static dslabs.kvstore.KVStoreWorkload.differentKeysInfiniteWorkload;
import static dslabs.kvstore.KVStoreWorkload.get;
import static dslabs.kvstore.KVStoreWorkload.getResult;
import static dslabs.kvstore.KVStoreWorkload.put;
import static dslabs.kvstore.KVStoreWorkload.putAppendGetWorkload;
import static dslabs.kvstore.KVStoreWorkload.putGetWorkload;
import static dslabs.kvstore.KVStoreWorkload.putOk;
import static dslabs.kvstore.KVStoreWorkload.simpleWorkload;
import static dslabs.primarybackup.PingCheckTimer.PING_CHECK_MILLIS;
import static dslabs.primarybackup.PingTimer.PING_MILLIS;
import static dslabs.primarybackup.ViewServerTest.INITIAL_VIEWNUM;
import static dslabs.primarybackup.ViewServerTest.TA;
import static dslabs.primarybackup.ViewServerTest.VSA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PrimaryBackupTest extends BaseJUnitTest {

    static StateGeneratorBuilder builder() {
        StateGeneratorBuilder builder = StateGenerator.builder();
        builder.serverSupplier(a -> {
            if (a.equals(VSA)) {
                return new ViewServer(a);
            } else {
                return new PBServer(a, VSA, new KVStore());
            }
        });
        builder.clientSupplier(a -> new PBClient(a, VSA));
        builder.workloadSupplier(KVStoreWorkload.emptyWorkload());
        return builder;
    }

    @Override
    protected void setupRunTest() {
        runState = new RunState(builder().build());
        runState.addServer(VSA);
    }

    @Override
    protected void setupSearchTest() {
        initSearchState = new SearchState(builder().build());
        initSearchState.addServer(VSA);
    }


    /* Predicates */
    private static StatePredicate hasViewReply(final int viewNum) {
        return StatePredicate
                .containsMessageMatching("ViewReply with viewNum: " + viewNum,
                        m -> (m instanceof ViewReply) &&
                                ((ViewReply) m).view().viewNum() >= viewNum);
    }

    private static StatePredicate hasViewReply(final int viewNum,
                                               final Address primary,
                                               final Address backup) {
        View v = new View(viewNum, primary, backup);
        return StatePredicate.containsMessageMatching("ViewReply with " + v,
                m -> (m instanceof ViewReply) &&
                        ((ViewReply) m).view().equals(v));
    }

    /* Search Test Helper Methods */

    /**
     * Takes the initial search state (after nodes have been added), and finds
     * the state where the required view has started, and all clients know about
     * the state.
     */
    private SearchState initView(SearchState startState, int viewNum,
                                 Address primary, Address backup,
                                 Address... clients) {
        System.out.println("Initializing view...");
        final View toStart = new View(viewNum, primary, backup);

        final List<Address> toInit = new LinkedList<>();
        toInit.add(primary);
        if (backup != null) {
            toInit.add(backup);
        }
        toInit.addAll(Arrays.asList(clients));

        // Find the right state
        StatePredicate viewRepliesSent = statePredicate(String.format(
                "ViewReply for %s sent to nodes %s, primary ack sent", toStart,
                toInit), s -> {
            Set<Address> viewReplyFound = new HashSet<>();
            boolean ackFound = false;

            for (MessageEnvelope me : s.network()) {
                Message m = me.message();
                if (m instanceof Ping && me.from().equals(primary) &&
                        ((Ping) m).viewNum() == toStart.viewNum()) {
                    ackFound = true;
                } else if (m instanceof ViewReply &&
                        ((ViewReply) m).view().equals(toStart)) {
                    viewReplyFound.add(me.to());
                }
            }

            return viewReplyFound.containsAll(toInit) && ackFound;
        });

        final SearchSettings temp = new SearchSettings();
        temp.maxTimeSecs(30).outputFreqSecs(-1)
            .addPrune(hasViewReply(viewNum + 1)).addPrune(hasViewReply(viewNum)
                .and(hasViewReply(viewNum, primary, backup).negate()))
            .networkActive(false).nodeActive(VSA, true)
            .addGoal(viewRepliesSent.and(hasViewReply(viewNum + 1).negate()));
        if (backup != null) {
            temp.linkActive(primary, backup, true)
                .linkActive(backup, primary, true);
        }

        bfs(startState, temp);
        SearchState current = goalMatchingState();
        clearSearchResults();

        // Deliver each of the view replies in turn
        for (Address a : toInit) {
            current = current.stepMessage(
                    new MessageEnvelope(VSA, a, new ViewReply(toStart)), null,
                    false);
        }

        // Deliver the Ack from the primary
        current = current.stepMessage(
                new MessageEnvelope(primary, VSA, new Ping(toStart.viewNum())),
                null, false);

        System.out.println("View initialized.\n");
        return current;
    }

    private SearchState initView(Address primary, Address backup,
                                 Address... clients) {
        return initView(initSearchState,
                backup == null ? INITIAL_VIEWNUM : INITIAL_VIEWNUM + 1, primary,
                backup, clients);
    }


    /* Run Test Helper Methods */

    /**
     * Takes the running state and polls the view server for the current view.
     * It uses runState in the class, which must currently be running.
     *
     * @return the view from the ViewServer
     */
    private View getView() {
        runState.network().send(new MessageEnvelope(TA, VSA, new GetView()));
        Event e = null;
        try {
            e = runState.network().take(TA);
        } catch (InterruptedException ex) {
            fail("Interrupted while waiting for view");
        }

        MessageEnvelope me = e.message();
        if (me == null) {
            fail("Polled envelope is null (this should never happen)");
        }
        Message m = me.message();
        if (!(m instanceof ViewReply)) {
            fail("Got non-ViewReply message in response to GetView");
        }

        return ((ViewReply) m).view();
    }

    /**
     * Waits until a view becomes active, or for 4 ping checks to elapse,
     * whichever comes first. If the expected view is not returned, throws an
     * error.
     *
     * @param primary
     *         the primary expected in the view (non-null)
     * @param backup
     *         the backup expected for the view (potentially null)
     */
    private void waitForView(Address primary, Address backup)
            throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            View v = getView();
            if (Objects.equals(primary, v.primary()) &&
                    Objects.equals(backup, v.backup())) {
                return;
            }
            Thread.sleep(PING_CHECK_MILLIS);
        }

        View v = getView();
        if (!(Objects.equals(primary, v.primary()) &&
                Objects.equals(backup, v.backup()))) {
            fail(String
                    .format("Expected view primary: %s, backup: %s did not start",
                            primary, backup));
        }
    }

    /**
     * Sets up a (non-running, uninitialized) state with the given view.
     *
     * @param primary
     *         the primary to install
     * @param backup
     *         the backup to install
     */
    private void setupRunView(Address primary, Address backup)
            throws InterruptedException {
        RunSettings temp = new RunSettings();
        runState.start(temp);
        runState.addServer(primary);
        waitForView(primary, null);
        if (backup != null) {
            runState.addServer(backup);
            waitForView(primary, backup);
        }
        // Sleep to make sure the view has started and been ack'd
        Thread.sleep(PING_CHECK_MILLIS * 4);
        runState.stop();
    }


    /* Tests */

    @Test(timeout = 2 * 1000, expected = InterruptedException.class)
    @PrettyTestName("Client throws InterruptedException")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test01ThrowsException() throws InterruptedException {
        final Thread mainThread = Thread.currentThread();
        Client client = runState.addClient(client(1));
        startThread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return;
            }

            mainThread.interrupt();
        });
        client.sendCommand(get("foo"));
        // Should never return since the runState wasn't started
        client.getResult();
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Single client, single server, simple operations")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test02Basic() throws InterruptedException {
        runState.addServer(server(1));
        runState.addClientWorker(client(1), simpleWorkload);

        runSettings.addInvariant(RESULTS_OK);
        runState.run(runSettings);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Primary chosen")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test03PrimaryChosen() throws InterruptedException {
        setupRunView(server(1), null);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Backup is chosen")
    @Category(RunTests.class)
    @TestPointValue(5)
    public void test04BackupChosen() throws InterruptedException {
        setupRunView(server(1), server(2));
    }

    @Test(timeout = 15 * 1000)
    @PrettyTestName("Count number of ViewServer requests")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test05MaxViewServerPingsCount() throws InterruptedException {
        runState.addServer(server(1));
        runState.addServer(server(2));
        Client client = runState.addClient(client(1));

        runState.start(runSettings);

        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            sendCommandAndCheck(client, put("xk" + i, i), putOk());
            sendCommandAndCheck(client, get("xk" + i), getResult(i));
            Thread.sleep(PING_MILLIS / 10);
        }

        long t2 = System.currentTimeMillis();
        int received = runState.network().numMessagesSentTo(VSA);

        double allowed =
                ((double) (t2 - t1)) / PING_MILLIS * runState.numNodes() * 2;

        if (received > allowed) {
            fail("Too many ViewServer messages: " + received + " (expected <=" +
                    allowed + ")");
        }
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Backup takes over")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test06BackupTakesOver() throws InterruptedException {
        runState.addServer(server(1));
        Client client = runState.addClient(client(1));

        runState.start(runSettings);

        sendCommandAndCheck(client, put("foo1", "bar1"), putOk());

        runState.addServer(server(2));
        waitForView(server(1), server(2));
        Thread.sleep(PING_CHECK_MILLIS * 4);

        sendCommandAndCheck(client, put("foo2", "bar2"), putOk());

        runState.removeNode(server(1));
        sendCommandAndCheck(client, get("foo1"), getResult("bar1"));
        sendCommandAndCheck(client, get("foo2"), getResult("bar2"));

        View v = getView();
        assertEquals(server(2), v.primary());
        assertNull(v.backup());
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Kill all servers")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test07KillLastServerRun() throws InterruptedException {
        setupRunView(server(1), server(2));
        Client client = runState.addClient(client(1));

        runState.start(runSettings);

        sendCommandAndCheck(client, put("foo", "bar"), putOk());

        // Simultaneously kill active nodes and start another
        runState.stop();
        runState.removeNode(server(1));
        runState.removeNode(server(2));
        runState.addServer(server(3));
        runState.start(runSettings);

        // Try to send an operation, shouldn't return
        client.sendCommand(get("foo"));

        Thread.sleep(PING_CHECK_MILLIS * 4);

        assertFalse(client.hasResult());
    }

    @Test(timeout = 20 * 1000)
    @PrettyTestName("At-most-once append")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(15)
    public void test08AtMostOnceUnreliable() throws InterruptedException {
        final int numRounds = 100;

        setupRunView(server(1), server(2));
        runState.addClientWorker(client(1),
                appendDifferentKeyWorkload(numRounds));

        runSettings.networkDeliverRate(0.8);

        runSettings.addInvariant(RESULTS_OK);
        runState.run(runSettings);
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Fail to new backup")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test09FailPut() throws InterruptedException {
        setupRunView(server(1), server(2));
        runState.addServer(server(3));
        Client client = runState.addClient(client(1));

        runState.start(runSettings);

        sendCommandAndCheck(client, put("a", "aa"), putOk());
        sendCommandAndCheck(client, put("b", "bb"), putOk());
        sendCommandAndCheck(client, put("c", "cc"), putOk());
        sendCommandAndCheck(client, get("a"), getResult("aa"));
        sendCommandAndCheck(client, get("b"), getResult("bb"));
        sendCommandAndCheck(client, get("c"), getResult("cc"));

        // Kill the backup
        runState.removeNode(server(2));
        sendCommandAndCheck(client, put("a", "aaa"), putOk());
        sendCommandAndCheck(client, get("a"), getResult("aaa"));
        waitForView(server(1), server(3));
        Thread.sleep(PING_CHECK_MILLIS * 4);
        sendCommandAndCheck(client, get("a"), getResult("aaa"));

        // Kill the primary
        runState.removeNode(server(1));
        sendCommandAndCheck(client, put("b", "bbb"), putOk());
        sendCommandAndCheck(client, get("b"), getResult("bbb"));
        waitForView(server(3), null);

        sendCommandAndCheck(client, get("a"), getResult("aaa"));
        sendCommandAndCheck(client, get("b"), getResult("bbb"));
        sendCommandAndCheck(client, get("c"), getResult("cc"));
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Concurrent puts, same keys, fail to backup")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test10ConcurrentPut() throws InterruptedException {
        final int nClients = 3, nKeys = 2, nPuts = 100;

        setupRunView(server(1), server(2));

        // Setup the clients and run the concurrent PUTs
        Random rand = new Random();
        for (int i = 1; i <= nClients; i++) {
            List<Command> workload = new LinkedList<>();
            for (int j = 0; j < nPuts; j++) {
                workload.add(put(rand.nextInt(nKeys), rand.nextInt()));
            }

            runState.addClientWorker(client(i),
                    KVStoreWorkload.workload(workload));
        }

        runState.run(runSettings);

        // Remove clients
        for (Address c : runState.clientWorkerAddresses()) {
            runState.removeNode(c);
        }

        runSettings.resetNetwork();

        // Let system fully heal
        runState.start(runSettings);
        Thread.sleep(PING_CHECK_MILLIS * 4);
        runState.stop();

        // Read from the primary
        Workload readKeys = Workload.builder().commands(
                IntStream.range(0, nKeys).mapToObj(KVStoreWorkload::get)
                         .collect(Collectors.toList())).build();
        runState.addClientWorker(new LocalAddress("client-readprimary"),
                readKeys);
        runState.run(runSettings);

        // Kill the primary
        runState.removeNode(server(1));
        runState.start(runSettings);
        waitForView(server(2), null);
        runState.stop();

        // Read from the old backup
        runState.addClientWorker(new LocalAddress("client-readbackup"),
                readKeys);

        // Ensure primary and backup had equal keys
        runSettings.addInvariant(ALL_RESULTS_SAME);

        runState.run(runSettings);
    }

    @Test(timeout = 10 * 1000)
    @PrettyTestName("Concurrent appends, same key, fail to backup")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test11ConcurrentAppend() throws InterruptedException {
        final int nClients = 3, nAppends = 100;

        setupRunView(server(1), server(2));

        // Setup the clients and run the concurrent APPENDS
        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i),
                    appendSameKeyWorkload(nAppends));
        }

        runState.run(runSettings);
        runSettings.addInvariant(APPENDS_LINEARIZABLE);
        assertRunInvariantsHold();

        // Remove clients
        for (Address c : runState.clientWorkerAddresses()) {
            runState.removeNode(c);
        }

        runSettings.resetNetwork();

        // Let system fully heal
        runState.start(runSettings);
        Thread.sleep(PING_CHECK_MILLIS * 4);
        runState.stop();

        // Read from the primary
        Workload readKeys = Workload.workload(get("foo"));
        runState.addClientWorker(new LocalAddress("client-primary"), readKeys);
        runState.run(runSettings);

        // Kill the primary
        runState.removeNode(server(1));
        runState.start(runSettings);
        waitForView(server(2), null);
        runState.stop();

        // Read from the old backup
        runState.addClientWorker(new LocalAddress("client-readbackup"),
                readKeys);

        // Ensure primary and backup had equal keys
        runSettings.clearInvariants().addInvariant(ALL_RESULTS_SAME);

        runState.run(runSettings);
    }

    @Test(timeout = 30 * 1000)
    @PrettyTestName("Concurrent puts, same keys, fail to backup")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(20)
    public void test12ConcurrentPutUnreliable() throws InterruptedException {
        // Just re-run the previous test with an unreliable network
        runSettings.networkDeliverRate(0.8);
        runSettings.nodeUnreliable(TA, false);
        test10ConcurrentPut();
    }

    @Test(timeout = 30 * 1000)
    @PrettyTestName("Concurrent appends, same key, fail to backup")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(20)
    public void test13ConcurrentAppendUnreliable() throws InterruptedException {
        // Just re-run the previous test with an unreliable network
        runSettings.networkDeliverRate(0.8);
        runSettings.nodeUnreliable(TA, false);
        test11ConcurrentAppend();
    }

    @Test(timeout = 50 * 1000)
    @PrettyTestName("Repeated crashes")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test14RepeatedCrashes() throws InterruptedException {
        final int nServers = 3, nClients = 3, testLengthSecs = 30;

        // Add the servers
        final List<Address> servers = new LinkedList<>();
        for (int i = 1; i <= nServers; i++) {
            Address server = server(i);
            servers.add(server);
            runState.addServer(server);
        }
        runState.start(runSettings);

        // Randomly crash and restart servers
        startThread(() -> {
            Random rand = new Random();
            int totalServers = nServers;

            try {
                Thread.sleep(PING_CHECK_MILLIS * 10);

                while (!Thread.interrupted()) {
                    Thread.sleep(PING_CHECK_MILLIS * 10);

                    Address toKill = servers.get(rand.nextInt(servers.size()));

                    // Add a new server with a new name
                    Address toAdd = server(++totalServers);
                    servers.add(toAdd);
                    runState.addServer(toAdd);

                    // Kill the old server
                    servers.remove(toKill);
                    runState.removeNode(toKill);
                }
            } catch (InterruptedException ignored) {
            }
        });

        for (int i = 0; i < nClients; i++) {
            runState.addClientWorker(client(i), differentKeysInfiniteWorkload,
                    false);
        }

        // Let the clients run
        Thread.sleep(testLengthSecs * 1000);

        // Shut the clients down
        shutdownStartedThreads();
        runState.stop();

        // Check if all the results were right
        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();

        // Reference implementation just over 1 sec
        assertMaxWaitTimeLessThan(5000);
    }

    @Test(timeout = 50 * 1000)
    @PrettyTestName("Repeated crashes")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(20)
    public void test15RepeatedCrashesUnreliable() throws InterruptedException {
        runSettings.networkDeliverRate(0.8).nodeUnreliable(VSA, false)
                   .nodeUnreliable(TA, false);
        test14RepeatedCrashes();
    }

    @Test
    @PrettyTestName("Single client, single server")
    @Category(SearchTests.class)
    @TestPointValue(15)
    public void test16SingleClientSearch() {
        initSearchState.addServer(server(1));
        initSearchState.addClientWorker(client(1), putAppendGetWorkload);

        // Make sure clients can finish
        searchSettings.addInvariant(RESULTS_OK).addGoal(CLIENTS_DONE)
                      .maxTimeSecs(30);
        bfs(initSearchState);
        assertGoalFound();

        // Make sure results match
        searchSettings.clearGoals().addPrune(CLIENTS_DONE).maxTimeSecs(30);
        bfs(initSearchState);
    }

    @Test
    @PrettyTestName("Single client, multi-server")
    @Category(SearchTests.class)
    @TestPointValue(15)
    public void test17SingleClientMultiServerSearch() {
        initSearchState.addServer(server(1));
        initSearchState.addServer(server(2));
        initSearchState.addServer(server(3));
        initSearchState.addClientWorker(client(1), putGetWorkload);

        final SearchState viewInitializedState =
                initView(server(1), server(2), client(1));

        // Make sure clients can finish
        searchSettings.addInvariant(RESULTS_OK).addGoal(CLIENTS_DONE)
                      .addPrune(hasViewReply(INITIAL_VIEWNUM + 2))
                      .maxTimeSecs(20).nodeActive(server(3), false);
        bfs(viewInitializedState);
        assertGoalFound();

        // Make sure results match
        searchSettings.clearGoals().clearPrunes().addPrune(CLIENTS_DONE)
                      .addPrune(hasViewReply(INITIAL_VIEWNUM + 3));
        bfs(viewInitializedState);

        searchSettings.clearPrunes().addPrune(CLIENTS_DONE);
        bfs(viewInitializedState);

        searchSettings.resetNetwork();
        bfs(viewInitializedState);
    }

    @Test
    @PrettyTestName("Multi-client, multi-server; writes visible")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test18MultiClientWritesVisibleSearch() {
        initSearchState.addServer(server(1));
        initSearchState.addServer(server(2));

        initSearchState.addClientWorker(client(1),
                Workload.workload(append("foo", "x")));
        initSearchState.addClientWorker(client(2),
                Workload.workload(append("foo", "y")));

        // Get a state w/ primary and backup initialized
        final SearchState viewInitialized =
                initView(server(1), server(2), client(1), client(2));

        // Find a state where clients have sent messages to primary
        System.out.println("Sending client requests...");
        final List<Address> senders = Lists.newArrayList(client(1), client(2));
        searchSettings.outputFreqSecs(-1).maxTimeSecs(20).networkActive(false)
                      .linkActive(client(1), server(1), true)
                      .linkActive(client(2), server(1), true)
                      .addInvariant(APPENDS_LINEARIZABLE).addGoal(
                statePredicate("Both clients sent messages to primary",
                        s -> Streams.stream(s.network())
                                    .filter(e -> e.to().equals(server(1)))
                                    .map(MessageEnvelope::from)
                                    .collect(Collectors.toSet())
                                    .containsAll(senders)));
        bfs(viewInitialized);
        final SearchState requestsSent = goalMatchingState();
        System.out.println("Client requests sent.\n");

        // Grab the messages sent by the clients
        final Map<Address, Set<MessageEnvelope>> sentMessages =
                Streams.stream(requestsSent.network().iterator())
                       .filter(e -> e.to().equals(server(1)))
                       .filter(e -> senders.contains(e.from())).collect(
                        Collectors
                                .toMap(MessageEnvelope::from, Sets::newHashSet,
                                        Sets::union));

        // Send the requests to the primary, keep track of the resulting messages
        final Map<Address, List<MessageEnvelope>> pToB = new HashMap<>();
        SearchState deliveredToP = requestsSent.clone();
        for (Address sender : senders) {
            List<MessageEnvelope> rs = new LinkedList<>();
            for (MessageEnvelope me : sentMessages.get(sender)) {
                deliveredToP = deliveredToP.stepMessage(me, null, false);
                rs.addAll(deliveredToP.newMessages());
            }
            pToB.put(sender, rs);
        }

        // Forward the messages to the backup in reverse order
        SearchState forwardedReversed = deliveredToP.clone();
        final Map<Address, List<MessageEnvelope>> bToP = new HashMap<>();
        for (Address sender : Lists.reverse(senders)) {
            List<MessageEnvelope> rs = new LinkedList<>();
            for (MessageEnvelope me : pToB.get(sender)) {
                forwardedReversed =
                        forwardedReversed.stepMessage(me, null, false);
                rs.addAll(forwardedReversed.newMessages());
            }
            bToP.put(sender, rs);
        }

        // Send the backup's message back to the primary in correct order
        for (Address sender : senders) {
            for (MessageEnvelope me : bToP.get(sender)) {
                forwardedReversed =
                        forwardedReversed.stepMessage(me, null, false);
            }
        }

        // Make sure clients can finish from here
        searchSettings.clear().addInvariant(APPENDS_LINEARIZABLE)
                      .addGoal(CLIENTS_DONE).maxTimeSecs(20);
        bfs(forwardedReversed);
        assertGoalFound();

        // Make sure linearizability is preserved
        searchSettings.clearGoals().addPrune(CLIENTS_DONE)
                      .addPrune(hasViewReply(INITIAL_VIEWNUM + 3)).addPrune(
                hasViewReply(INITIAL_VIEWNUM + 2, server(1), null))
                      .maxTimeSecs(30);
        bfs(forwardedReversed);

        // Do the same thing, but this time, only forward second request to backup
        SearchState onlySecondForwarded = deliveredToP.clone();
        final List<MessageEnvelope> bToP2 = new LinkedList<>();
        for (MessageEnvelope me : pToB.get(client(2))) {
            onlySecondForwarded =
                    onlySecondForwarded.stepMessage(me, null, false);
            bToP2.addAll(onlySecondForwarded.newMessages());
        }
        for (MessageEnvelope me : bToP2) {
            onlySecondForwarded =
                    onlySecondForwarded.stepMessage(me, null, false);
        }
        bfs(onlySecondForwarded);

        // Finally, do one last BFS from the very beginning when requests were sent
        bfs(requestsSent);
    }

    @Test
    @PrettyTestName("Multi-client, multi-server; multiple failures to backup")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test19MultipleFailuresSearch() {
        initSearchState.addServer(server(1));
        initSearchState.addServer(server(2));

        initSearchState.addClientWorker(client(1),
                KVStoreWorkload.builder().commands(append("foo", "x"))
                               .results(appendResult("x")).build());
        initSearchState.addClientWorker(client(2),
                KVStoreWorkload.builder().commands(append("foo", "y"))
                               .results(appendResult("xy")).build());

        // Add primary and fail to it
        final SearchState firstView =
                initView(initSearchState, INITIAL_VIEWNUM + 1, server(1),
                        server(2));
        final SearchState primaryAlone =
                initView(firstView, INITIAL_VIEWNUM + 2, server(1), null,
                        client(1));

        // Have the client commit the operation to only the primary
        searchSettings.maxTimeSecs(10).partition(server(1), client(1), VSA)
                      .addInvariant(RESULTS_OK).addGoal(clientDone(client(1)))
                      .addPrune(hasViewReply(INITIAL_VIEWNUM + 3));
        bfs(primaryAlone);
        final SearchState client1Done = goalMatchingState();

        // Make sure that the second client can finish, sending message to backup
        searchSettings.maxTimeSecs(30).resetNetwork()
                      .partition(server(1), server(2), client(2), VSA)
                      .linkActive(server(1), client(2), false)
                      .linkActive(client(2), server(1), false).clearGoals()
                      .addGoal(CLIENTS_DONE).clearPrunes().addPrune(
                hasViewReply(INITIAL_VIEWNUM + 3).implies(
                        hasViewReply(INITIAL_VIEWNUM + 3, server(1), server(2)))
                                                 .negate()).addPrune(
                hasViewReply(INITIAL_VIEWNUM + 4).implies(
                        hasViewReply(INITIAL_VIEWNUM + 4, server(2), null))
                                                 .negate())
                      .addPrune(hasViewReply(INITIAL_VIEWNUM + 5));
        bfs(client1Done);
        assertGoalFound();

        searchSettings.clearGoals();
        bfs(client1Done);
    }

    @Test
    @PrettyTestName("Multi-client, multi-server random depth-first search")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test20RandomSearch() {
        initSearchState.addServer(server(1));
        initSearchState.addServer(server(2));
        initSearchState.addServer(server(3));

        initSearchState.addClientWorker(client(1),
                KVStoreWorkload.builder().commands(append("foo", "x")).build());
        initSearchState.addClientWorker(client(2),
                KVStoreWorkload.builder().commands(append("foo", "y")).build());

        searchSettings.maxDepth(1000).maxTimeSecs(45)
                      .addInvariant(APPENDS_LINEARIZABLE)
                      .addPrune(CLIENTS_DONE);

        dfs(initSearchState);
    }
}
