package dslabs.shardkv;

import com.google.common.collect.Sets;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Result;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.junit.PrettyTestName;
import dslabs.framework.testing.junit.RunTests;
import dslabs.framework.testing.junit.SearchTests;
import dslabs.framework.testing.junit.TestPointValue;
import dslabs.framework.testing.junit.UnreliableTests;
import dslabs.kvstore.KVStoreWorkload;
import dslabs.shardmaster.ShardMaster.Join;
import dslabs.shardmaster.ShardMaster.Leave;
import dslabs.shardmaster.ShardMaster.Move;
import dslabs.shardmaster.ShardMaster.Ok;
import dslabs.shardmaster.ShardMaster.ShardConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;

import static dslabs.framework.testing.StatePredicate.CLIENTS_DONE;
import static dslabs.framework.testing.StatePredicate.RESULTS_OK;
import static dslabs.kvstore.KVStoreWorkload.append;
import static dslabs.kvstore.KVStoreWorkload.appendResult;
import static dslabs.kvstore.KVStoreWorkload.appendsLinearizable;
import static dslabs.kvstore.KVStoreWorkload.get;
import static dslabs.kvstore.KVStoreWorkload.getResult;
import static dslabs.kvstore.KVStoreWorkload.put;
import static dslabs.kvstore.KVStoreWorkload.putOk;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class ShardStorePart1Test extends ShardStoreBaseTest {
    @Test(timeout = 5 * 1000)
    @PrettyTestName("Single group, basic workload")
    @Category(RunTests.class)
    @TestPointValue(10)
    public void test01Basic() throws InterruptedException {
        int numGroups = 1, numServersPerGroup = 3, numShardMasters = 3,
                numShards = 10;

        setupStates(numGroups, numServersPerGroup, numShardMasters, numShards);
        runState.addClientWorker(client(1), KVStoreWorkload.simpleWorkload);

        runState.start(runSettings);
        joinGroup(1, numServersPerGroup);

        runState.waitFor();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
    }

    @Test(timeout = 20 * 1000)
    @PrettyTestName("Multi-group join/leave")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test02JoinLeave() throws InterruptedException {
        int numServersPerGroup = 3, numShardMasters = 3, numShards = 10;

        setupStates(3, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        joinGroup(1, 3);

        Client client = runState.addClient(client(1));
        Map<String, String> kv = new HashMap<>();
        for (int i = 1; i <= 100; i++) {
            String key = "key-" + i;
            String value = RandomStringUtils.randomAlphanumeric(8);
            sendCommandAndCheck(client, put(key, value), putOk());
            kv.put(key, value);
        }

        // Add groups and check that keys are still there
        joinGroup(2, numServersPerGroup);
        joinGroup(3, numServersPerGroup);
        Thread.sleep(5000);

        for (int i = 1; i <= 100; i++) {
            String key = "key-" + i;
            sendCommandAndCheck(client, get(key), getResult(kv.get(key)));
        }

        // Replace keys
        for (int i = 1; i <= 100; i++) {
            String key = "key-" + i;
            String value = RandomStringUtils.randomAlphanumeric(8);
            sendCommandAndCheck(client, put(key, value), putOk());
            kv.put(key, value);
        }

        // Remove groups
        removeGroup(1);
        removeGroup(2);
        Thread.sleep(5000);

        // Check the keys
        for (int i = 1; i <= 100; i++) {
            String key = "key-" + i;
            sendCommandAndCheck(client, get(key), getResult(kv.get(key)));
        }
    }

    @Test(timeout = 25 * 1000)
    @PrettyTestName("Shards move when group joins")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test03ShardsMoveOnJoin() throws InterruptedException {
        int numServersPerGroup = 3, numShardMasters = 3, numShards = 100;

        setupStates(2, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        joinGroup(1, 3);

        Client client = runState.addClient(client(1));
        Map<String, String> kv = new HashMap<>();

        for (int i = 1; i <= numShards; i++) {
            String key = keyForShard(i);
            String value = RandomStringUtils.randomAlphanumeric(8);

            sendCommandAndCheck(client, put(key, value), putOk());
            kv.put(key, value);
        }

        // Add group and then kill group 1 servers
        joinGroup(2, numServersPerGroup);

        Thread.sleep(5000);

        for (int i = 1; i <= numServersPerGroup; i++) {
            runState.removeNode(server(1, i));
        }

        // Add a client for each shard
        int i = 2;
        for (String key : kv.keySet()) {
            runState.addClientWorker(client(i),
                    KVStoreWorkload.workload(get(key)));
            i++;
        }

        Thread.sleep(10000);

        runState.stop();

        // Count number of keys gotten
        int numGets = runState.results().size();
        long numFound =
                runState.results().values().stream().filter(l -> l.size() > 0)
                        .count();

        assertTrue(numFound > numShards / 3 && numFound < 2 * numShards / 3);
    }

    @Test(timeout = 25 * 1000)
    @PrettyTestName("Shards move when moved by ShardMaster")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test04ShardsMoveOnMove() throws InterruptedException {
        int numServersPerGroup = 3, numShardMasters = 3, numShards = 100;

        setupStates(2, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        joinGroup(1, 3);

        Client client = runState.addClient(client(1));
        Map<String, String> kv = new HashMap<>();

        for (int i = 1; i <= numShards; i++) {
            String key = keyForShard(i);
            String value = RandomStringUtils.randomAlphanumeric(32);
            sendCommandAndCheck(client, put(key, value), putOk());
            kv.put(key, value);
        }

        // Add group, move 10 shards to it, kill group 1
        joinGroup(2, numServersPerGroup);

        ShardConfig config1 = getConfig();
        Set<Integer> toMove = new HashSet<>();
        toMove.addAll(config1.groupInfo().get(1).getRight().stream().limit(10)
                             .collect(Collectors.toSet()));
        assertTrue(toMove.size() >= 10);

        for (int shard : toMove) {
            sendCommandAndCheck(configController, new Move(2, shard), new Ok());
        }

        ShardConfig config2 = getConfig();

        Set<Integer> group2Shards = config2.groupInfo().get(2).getRight();

        assertEquals(group2Shards,
                Sets.union(config1.groupInfo().get(2).getRight(), toMove));

        Thread.sleep(5000);

        for (int i = 1; i <= numServersPerGroup; i++) {
            runState.removeNode(server(1, i));
        }

        // Add a client for each shard
        int i = 2;
        Set<Address> group2Clients = new HashSet<>();
        Set<Address> group1Clients = new HashSet<>();
        for (String key : kv.keySet()) {
            runState.addClientWorker(client(i), KVStoreWorkload
                    .workload(Collections.singletonList(get(key)),
                            Collections.singletonList(getResult(kv.get(key)))));

            if (group2Shards
                    .contains(ShardStoreNode.keyToShard(key, numShards))) {
                group2Clients.add(client(i));
            } else {
                group1Clients.add(client(i));
            }

            i++;
        }

        Thread.sleep(10000);

        runState.stop();

        // Count number of keys gotten
        runSettings.addInvariant(RESULTS_OK);
        runSettings.addInvariant(StatePredicate
                .statePredicate("Only group 2 operations completed", s -> {
                    for (Entry<Address, List<Result>> e : s.results()
                                                           .entrySet()) {
                        Address a = e.getKey();
                        if (!group2Clients.contains(a) &&
                                !group1Clients.contains(a)) {
                            continue;
                        }

                        if (e.getValue().isEmpty() &&
                                group2Clients.contains(a)) {
                            return false;
                        }

                        if (!e.getValue().isEmpty() &&
                                group1Clients.contains(a)) {
                            return false;
                        }
                    }
                    return true;
                }));
    }

    @Test(timeout = 20 * 1000)
    @PrettyTestName("Progress with majorities in each group")
    @Category(RunTests.class)
    @TestPointValue(15)
    public void test05ProgressWithMajorities() throws InterruptedException {
        // Remove one server per group
        for (int g = 1; g <= 3; g++) {
            runSettings.receiverActive(server(g, 3), false);
            runSettings.senderActive(server(g, 3), false);
        }
        runSettings.receiverActive(shardMaster(3), false);
        runSettings.senderActive(shardMaster(3), false);

        // Re-run join/leave test
        test02JoinLeave();
    }

    @Test(timeout = 60 * 1000)
    @PrettyTestName("Repeated partitioning of each group")
    @Category(RunTests.class)
    @TestPointValue(20)
    public void test06RepeatedPartitioning() throws InterruptedException {
        int numGroups = 3, numServersPerGroup = 3, numShardMasters = 3,
                numShards = 10, testLengthSecs = 50, nClients = 5;

        setupStates(numGroups, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        for (int g = 1; g <= numGroups; g++) {
            joinGroup(g, numServersPerGroup);
        }

        // Startup the clients with 10ms inter-request delay
        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i),
                    KVStoreWorkload.differentKeysInfiniteWorkload(10), false);
        }

        // Re-partition -> 2s -> unpartition -> 2s
        startThread(() -> {
            try {
                while (!Thread.interrupted()) {
                    runSettings.reconnect();

                    // For each group, turn a minority of servers off
                    for (int g = 1; g <= numGroups; g++) {
                        final int groupNum = g;

                        List<Address> servers =
                                IntStream.rangeClosed(1, numServersPerGroup)
                                         .mapToObj(j -> server(groupNum, j))
                                         .collect(Collectors.toList());
                        Collections.shuffle(servers);

                        for (int j = 0; (j + 1) * 2 < numServersPerGroup; j++) {
                            runSettings.nodeActive(servers.get(j), false);
                        }
                    }
                    Thread.sleep(2000);

                    runSettings.reconnect();
                    Thread.sleep(2000);
                }
            } catch (InterruptedException ignored) {
            }
        });

        // Let the clients run
        Thread.sleep(testLengthSecs * 1000);

        // Shut the clients down
        shutdownStartedThreads();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();

        // Make sure maximum wait is below 2s (should be much less)
        assertMaxWaitTimeLessThan(2000);
    }

    @Test(timeout = 60 * 1000)
    @PrettyTestName("Repeated shard movement")
    @Category(RunTests.class)
    @TestPointValue(20)
    public void test07ConstantMovement() throws InterruptedException {
        int numGroups = 3, numServersPerGroup = 3, numShardMasters = 3,
                numShards = 10, testLengthSecs = 50, nClients = 5;

        setupStates(numGroups, numServersPerGroup, numShardMasters, numShards);

        runState.start(runSettings);

        for (int g = 1; g <= numGroups; g++) {
            joinGroup(g, numServersPerGroup);
        }

        // Startup the clients
        for (int i = 1; i <= nClients; i++) {
            runState.addClientWorker(client(i),
                    KVStoreWorkload.differentKeysInfiniteWorkload, false);
        }

        // Constantly move shards around
        startThread(moveShards(numGroups, numShards));

        // Let the clients run
        Thread.sleep(testLengthSecs * 1000);

        // Shut the clients down
        shutdownStartedThreads();
        runState.stop();

        runSettings.addInvariant(RESULTS_OK);
        assertRunInvariantsHold();
        assertMaxWaitTimeLessThan(4000);
    }

    @Test(timeout = 40 * 1000)
    @PrettyTestName("Multi-group join/leave")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(20)
    public void test08JoinLeaveUnreliable() throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        test02JoinLeave();
    }

    @Test(timeout = 60 * 1000)
    @PrettyTestName("Repeated shard movement")
    @Category({RunTests.class, UnreliableTests.class})
    @TestPointValue(30)
    public void test09ConstantMovementUnreliable() throws InterruptedException {
        runSettings.networkDeliverRate(0.8);
        test07ConstantMovement();
    }

    @Test
    @PrettyTestName("Single client, single group")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test10SingleClientSingleGroupSearch() {
        setupStates(1, 1, 1, 10);
        initSearchState
                .addClientWorker(client(1), KVStoreWorkload.putGetWorkload);
        singleClientSingleGroupSearch();
    }

    @Test
    @PrettyTestName("Single client, multi-group")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test11SingleClientMultiGroupSearch() {
        setupStates(2, 1, 1, 10);
        initSearchState
                .addClientWorker(client(1), KVStoreWorkload.putGetWorkload);
        singleClientMultiGroupSearch();
    }

    @Test
    @PrettyTestName("Multi-client, multi-group")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test12MultiClientMultiGroupSearch() {
        setupStates(2, 1, 1, 2);

        Workload w1 = KVStoreWorkload.builder().commands(append("foo-1", "X1"),
                append("foo-2", "X2")).results(appendResult("X1"),
                appendResult("X2")).build();
        initSearchState.addClientWorker(client(1), w1);

        Workload w2 = KVStoreWorkload.builder().commands(append("foo-1", "Y1"),
                append("foo-2", "Y2")).results(appendResult("X1Y1"),
                appendResult("X2Y2")).build();
        initSearchState.addClientWorker(client(2), w2);

        multiClientMultiGroupSearch();
    }

    private void randomSearch(int numServersPerGroup) {
        setupStates(2, numServersPerGroup, 1, 2);

        Workload ccWorkload = Workload.builder().commands(
                new Join(1, servers(1, numServersPerGroup)),
                new Join(2, servers(2, numServersPerGroup)), new Leave(1))
                                      .results(new Ok(), new Ok(), new Ok())
                                      .build();
        initSearchState.addClientWorker(CCA, ccWorkload);

        Workload w1 = KVStoreWorkload.builder().commands(append("foo-1", "X"),
                append("foo-1", "Y")).build();
        initSearchState.addClientWorker(client(1), w1);

        Workload w2 = KVStoreWorkload.builder().commands(append("foo-1", "Z"))
                                     .build();
        initSearchState.addClientWorker(client(2), w2);

        Workload w3 = KVStoreWorkload.builder().commands(append("foo-2", "X"),
                append("foo-2", "Y")).build();
        initSearchState.addClientWorker(client(3), w3);

        Workload w4 = KVStoreWorkload.builder().commands(append("foo-2", "Z"))
                                     .build();
        initSearchState.addClientWorker(client(4), w4);

        searchSettings.maxDepth(1000).maxTimeSecs(20)
                      .addInvariant(appendsLinearizable(client(1), client(2)))
                      .addInvariant(appendsLinearizable(client(3), client(4)))
                      .addInvariant(RESULTS_OK).addPrune(CLIENTS_DONE);

        dfs(initSearchState);

    }

    @Test
    @PrettyTestName("One server per group random search")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test13SingleServerRandomSearch() {
        randomSearch(1);
    }

    @Test
    @PrettyTestName("Multiple servers per group random search")
    @Category(SearchTests.class)
    @TestPointValue(20)
    public void test14MultiServerRandomSearch() {
        randomSearch(3);
    }
}
