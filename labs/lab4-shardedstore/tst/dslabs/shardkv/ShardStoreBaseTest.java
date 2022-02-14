package dslabs.shardkv;

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.testing.AbstractState;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StateGenerator.StateGeneratorBuilder;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.junit.BaseJUnitTest;
import dslabs.framework.testing.runner.RunState;
import dslabs.framework.testing.search.SearchState;
import dslabs.kvstore.KVStoreWorkload;
import dslabs.paxos.PaxosClient;
import dslabs.paxos.PaxosServer;
import dslabs.shardmaster.ShardMaster;
import dslabs.shardmaster.ShardMaster.Join;
import dslabs.shardmaster.ShardMaster.Leave;
import dslabs.shardmaster.ShardMaster.Move;
import dslabs.shardmaster.ShardMaster.Ok;
import dslabs.shardmaster.ShardMaster.Query;
import dslabs.shardmaster.ShardMaster.ShardConfig;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dslabs.framework.testing.StatePredicate.CLIENTS_DONE;
import static dslabs.framework.testing.StatePredicate.RESULTS_OK;
import static dslabs.framework.testing.StatePredicate.clientDone;
import static dslabs.framework.testing.StatePredicate.clientHasResults;
import static org.junit.Assert.assertTrue;

public abstract class ShardStoreBaseTest extends BaseJUnitTest {
    static final Address CCA = new LocalAddress("configController");

    Client configController;


    /* Setup and cleanup */

    static StateGeneratorBuilder builder(final int numGroups,
                                         final int numServersPerGroup,
                                         final int numShardMasters,
                                         final int numShards) {
        final StateGeneratorBuilder builder = StateGenerator.builder();
        final Address[] shardMasters = IntStream.rangeClosed(1, numShardMasters)
                                                .mapToObj(
                                                        ShardStoreBaseTest::shardMaster)
                                                .toArray(Address[]::new);

        builder.serverSupplier(a -> {
            if (Arrays.asList(shardMasters).contains(a)) {
                return new PaxosServer(a, shardMasters.clone(),
                        new ShardMaster(numShards));

            } else {
                Matcher m = Pattern.compile("server(\\d+)-(\\d+)")
                                   .matcher(a.toString());

                if (m.find()) {
                    int groupId = Integer.parseInt(m.group(1));

                    Address[] group =
                            IntStream.rangeClosed(1, numServersPerGroup)
                                     .mapToObj(i -> server(groupId, i))
                                     .toArray(Address[]::new);

                    return new ShardStoreServer(a, shardMasters.clone(),
                            numShards, group.clone(), groupId);
                }

                throw new RuntimeException(
                        "Can't create server for given address.");
            }
        });

        builder.clientSupplier(a -> {
            if (a.equals(CCA)) {
                return new PaxosClient(a, shardMasters.clone());
            }

            return new ShardStoreClient(a, shardMasters.clone(), numShards);
        });
        builder.workloadSupplier(KVStoreWorkload.emptyWorkload());
        return builder;
    }

    static void addServers(AbstractState state, int numGroups,
                           int numServersPerGroup, int numShardMasters) {
        for (int i = 1; i <= numShardMasters; i++) {
            state.addServer(shardMaster(i));
        }

        for (int g = 1; g <= numGroups; g++) {
            for (int i = 1; i <= numServersPerGroup; i++) {
                state.addServer(server(g, i));
            }
        }
    }

    @Override
    protected void cleanupTest() {
        configController = null;
    }

    void setupStates(int numGroups, int numServersPerGroup, int numShardMasters,
                     int numShards) {
        StateGenerator stateGenerator =
                builder(numGroups, numServersPerGroup, numShardMasters,
                        numShards).build();

        if (isRunTest()) {
            runState = new RunState(stateGenerator);
            addServers(runState, numGroups, numServersPerGroup,
                    numShardMasters);
            configController = runState.addClient(CCA);
        }

        if (isSearchTest()) {
            initSearchState = new SearchState(stateGenerator);
            addServers(initSearchState, numGroups, numServersPerGroup,
                    numShardMasters);
        }
    }


    /* Addresses */

    static Address shardMaster(int i) {
        return new LocalAddress("shardmaster" + i);
    }

    static Address server(int groupNum, int i) {
        return new LocalAddress("server" + groupNum + "-" + i);
    }

    static Set<Address> servers(int groupNum, int numServers) {
        return IntStream.rangeClosed(1, numServers)
                        .mapToObj(i -> server(groupNum, i))
                        .collect(Collectors.toSet());
    }


    /* Run Test Utils */

    void joinGroup(int groupNum, int numServersPerGroup)
            throws InterruptedException {
        sendCommandAndCheck(configController,
                new Join(groupNum, servers(groupNum, numServersPerGroup)),
                new Ok());
    }

    void removeGroup(int groupNum) throws InterruptedException {
        sendCommandAndCheck(configController, new Leave(groupNum), new Ok());
    }

    ShardConfig getConfig(int configNum) throws InterruptedException {
        configController.sendCommand(new Query(configNum));
        Serializable result = configController.getResult();
        assertTrue(result instanceof ShardConfig);
        return (ShardConfig) result;
    }

    ShardConfig getConfig() throws InterruptedException {
        return getConfig(-1);
    }

    void assertConfigBalanced() throws InterruptedException {
        ShardConfig config = getConfig();
        Integer max = config.groupInfo().values().stream()
                            .map(p -> p.getRight().size())
                            .max(Integer::compareTo).orElse(null);
        Integer min = config.groupInfo().values().stream()
                            .map(p -> p.getRight().size())
                            .min(Integer::compareTo).orElse(null);
        assertTrue(max != null && min != null);
        assertTrue(max - min <= 1);
    }

    Runnable moveShards(int numGroups, int numShards) {
        return () -> {
            Random rand = new Random();

            try {
                while (!Thread.interrupted()) {
                    Thread.sleep(4000);
                    int groupNum = rand.nextInt(numGroups) + 1;
                    int shardNum = rand.nextInt(numShards) + 1;
                    configController.sendCommand(new Move(groupNum, shardNum));
                    configController.getResult();
                }
            } catch (InterruptedException ignored) {
            }
        };
    }


    /* Other Utils */

    String keyForShard(int shardNum) {
        return "key-" + shardNum;
    }


    /* Common Search Tests */

    /**
     * Runs a single-client, single-group search. The client should be
     * client(1). Must call setupStates(1, 1, 1, N) before calling this method.
     */
    void singleClientSingleGroupSearch() {
        initSearchState.addClientWorker(CCA,
                Workload.builder().commands(new Join(1, servers(1, 1)))
                        .results(new Ok()).build());

        // First, just get the Join finished
        searchSettings.maxTimeSecs(15).partition(CCA, shardMaster(1))
                      .addInvariant(RESULTS_OK).addGoal(clientDone(CCA));
        bfs(initSearchState);
        final SearchState joinFinished = goalMatchingState();

        // From there, make sure the client can finish all operations
        searchSettings.resetNetwork().clearGoals().addGoal(CLIENTS_DONE);
        bfs(joinFinished);
        assertGoalFound();

        // Now, check from the end of the Join
        searchSettings.clearGoals().addPrune(CLIENTS_DONE).maxTimeSecs(30);
        bfs(joinFinished);

        // Search from the beginning with no timers (potentially not useful)
        searchSettings.deliverTimers(false);
        bfs(initSearchState);
    }

    /**
     * Runs a single-client, multi-group search. The client should be client(1).
     * Must call setupStates(2, 1, 1, N) before calling this method.
     */
    void singleClientMultiGroupSearch() {
        // Group 1 joins -> group 2 joins -> group 1 leaves
        initSearchState.addClientWorker(CCA, Workload.builder().commands(
                new Join(1, servers(1, 1)), new Join(2, servers(2, 1)),
                new Leave(1)).results(new Ok(), new Ok(), new Ok()).build());

        // Find state where first Join is finished
        searchSettings.maxTimeSecs(15).partition(CCA, shardMaster(1))
                      .addInvariant(RESULTS_OK)
                      .addGoal(clientHasResults(CCA, 1));
        bfs(initSearchState);
        final SearchState firstJoin = goalMatchingState();

        // Then, find a state where the Put is finished
        searchSettings.resetNetwork()
                      .partition(client(1), shardMaster(1), server(1, 1))
                      .clearGoals().addGoal(clientHasResults(client(1), 1));
        bfs(firstJoin);
        final SearchState putDone = goalMatchingState();

        // From there, finish the second Join and the Leave
        searchSettings.resetNetwork().partition(CCA, shardMaster(1))
                      .clearGoals().addGoal(clientDone(CCA));
        bfs(putDone);
        final SearchState ccaDone = goalMatchingState();

        // Search for invariant violations from there
        searchSettings.clearGoals().resetNetwork().addPrune(CLIENTS_DONE)
                      .maxTimeSecs(30);
        bfs(ccaDone);

        // Search for invariant violations from first Join
        bfs(firstJoin);

        // Again without timers (potentially not useful)
        searchSettings.deliverTimers(false).maxTimeSecs(15);
        bfs(firstJoin);
    }

    /**
     * Runs a multi-client, multi-group search. The clients should be client(1),
     * client(2). This method forces client(1)'s commands to be executed before
     * client(2)'s. Must call setupStates(2, 1, 1, N) before calling this
     * method.
     */
    void multiClientMultiGroupSearch() {
        // Both groups join
        initSearchState.addClientWorker(CCA, Workload.builder().commands(
                new Join(1, servers(1, 1)), new Join(2, servers(2, 1)))
                                                     .build());

        // Find state where first join is finished
        searchSettings.maxTimeSecs(15).partition(CCA, shardMaster(1))
                      .addInvariant(RESULTS_OK)
                      .addGoal(clientHasResults(CCA, 1));
        bfs(initSearchState);
        SearchState firstJoin = goalMatchingState();

        // Find state where client1 is done
        searchSettings.resetNetwork()
                      .partition(client(1), shardMaster(1), server(1, 1))
                      .maxTimeSecs(30).clearGoals()
                      .addGoal(clientDone(client(1)));
        bfs(firstJoin);
        SearchState client1Done = goalMatchingState();

        // Make sure we can find a state where client2 has finished
        searchSettings.resetNetwork()
                      .partition(client(2), shardMaster(1), server(1, 1))
                      .clearGoals().addGoal(clientDone(client(2)));
        bfs(client1Done);

        // From here, finish the other join
        searchSettings.resetNetwork().maxTimeSecs(15)
                      .partition(CCA, shardMaster(1)).clearGoals()
                      .addGoal(clientDone(CCA));
        bfs(client1Done);
        SearchState secondJoin = goalMatchingState();

        // Search for invariant violations from second join being done
        searchSettings.clearGoals().resetNetwork().maxTimeSecs(30)
                      .addPrune(CLIENTS_DONE);
        bfs(secondJoin);

        // Again without timers (potentially not useful)
        searchSettings.deliverTimers(false);
        bfs(secondJoin);
    }
}
