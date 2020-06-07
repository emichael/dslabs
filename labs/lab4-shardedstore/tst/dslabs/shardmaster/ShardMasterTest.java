package dslabs.shardmaster;

import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Result;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.junit.DSLabsTestRunner;
import dslabs.framework.testing.junit.PrettyTestName;
import dslabs.framework.testing.junit.TestPointValue;
import dslabs.framework.testing.utils.Cloning;
import dslabs.shardmaster.ShardMaster.Error;
import dslabs.shardmaster.ShardMaster.Join;
import dslabs.shardmaster.ShardMaster.Leave;
import dslabs.shardmaster.ShardMaster.Move;
import dslabs.shardmaster.ShardMaster.Ok;
import dslabs.shardmaster.ShardMaster.Query;
import dslabs.shardmaster.ShardMaster.ShardConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static dslabs.shardmaster.ShardMaster.INITIAL_CONFIG_NUM;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(DSLabsTestRunner.class)
public class ShardMasterTest {
    private static final int DEFAULT_NUM_SHARDS = 10;

    private ShardMaster shardMaster;
    private int maxConfigSeen;
    private Map<Integer, ShardConfig> seen;

    @Before
    public void setup() {
        shardMaster = new ShardMaster(DEFAULT_NUM_SHARDS);
        maxConfigSeen = -1;
        seen = new HashMap<>();
    }

    private Set<Integer> fullShardRange(int numShards) {
        return IntStream.rangeClosed(1, numShards).boxed()
                        .collect(Collectors.toSet());
    }

    private Set<Address> group(int i) {
        return IntStream.rangeClosed(3 * i - 2, 3 * i)
                        .mapToObj(j -> new LocalAddress("server" + j))
                        .collect(Collectors.toSet());
    }

    private Result execute(Command command) {
        Result r = shardMaster.execute(command);
        return Cloning.clone(r);
    }

    private ShardConfig getConfig(int configNum, boolean checkIsNext,
                                  boolean checkFresh) {
        Result result = execute(new Query(configNum));

        // Check that doing the same thing twice returns the same result
        Result secondTry = execute(new Query(configNum));
        assertEquals(result, secondTry);

        assertTrue(result instanceof ShardConfig);
        ShardConfig config = (ShardConfig) result;

        if (configNum >= INITIAL_CONFIG_NUM) {
            assertTrue(configNum >= config.configNum());
        } else if (checkFresh) {
            assertTrue(config.configNum() >= maxConfigSeen);
        }

        if (seen.containsKey(config.configNum())) {
            if (checkIsNext) {
                fail("Got an old configuration.");
            }
            assertEquals(seen.get(config.configNum()), config);
        } else {
            if (checkIsNext) {
                assertEquals(maxConfigSeen + 1, config.configNum());
            }
            seen.put(config.configNum(), config);
        }

        if (config.configNum() > maxConfigSeen) {
            maxConfigSeen = config.configNum();
        }

        return config;
    }

    private ShardConfig getLatest(boolean checkIsNext) {
        return getConfig(-1, checkIsNext, true);
    }

    private void checkConfig(int numMoved, int numShards, ShardConfig config,
                             Integer... groupIds) {
        // Check that mapping is balanced
        Integer max = config.groupInfo().values().stream()
                            .map(p -> p.getRight().size())
                            .max(Integer::compareTo).orElse(null);
        Integer min = config.groupInfo().values().stream()
                            .map(p -> p.getRight().size())
                            .min(Integer::compareTo).orElse(null);
        assertTrue(max != null && min != null);
        assertTrue(max - min <= 1 + (2 * numMoved));

        // Check that groups have the right addresses
        assertEquals(new HashSet<>(Arrays.asList(groupIds)),
                config.groupInfo().keySet());
        for (Integer gid : config.groupInfo().keySet()) {
            assertEquals(group(gid), config.groupInfo().get(gid).getLeft());
        }

        // Check mappings are distinct and union to the full shard range
        Set<Integer> seen = new HashSet<>();
        for (Integer gid : config.groupInfo().keySet()) {
            Set<Integer> shards = config.groupInfo().get(gid).getRight();
            for (int s : shards) {
                assertFalse(seen.contains(s));
                seen.add(s);
            }
        }
        assertEquals(fullShardRange(numShards), seen);
    }

    private void checkConfig(ShardConfig config, Integer... groupIds) {
        checkConfig(0, DEFAULT_NUM_SHARDS, config, groupIds);
    }

    private void checkShardMovement(ShardConfig previous, ShardConfig current,
                                    int numShards) {
        assertEquals(previous.configNum() + 1, current.configNum());

        int numMoved = previous.groupInfo().keySet().stream().mapToInt(gid -> {
            Set<Integer> p =
                    new HashSet<>(previous.groupInfo().get(gid).getRight());
            if (current.groupInfo().containsKey(gid)) {
                p.removeAll(current.groupInfo().get(gid).getRight());
            }
            return p.size();
        }).sum();

        int previousNumGroups = previous.groupInfo().size(), currentNumGroups =
                current.groupInfo().size();

        // Can never move more than one group at a time
        assertTrue(Math.abs(previousNumGroups - currentNumGroups) <= 1);

        if (previousNumGroups < currentNumGroups) {
            int newGroup = current.groupInfo().keySet().stream()
                                  .filter(gid -> !previous.groupInfo()
                                                          .containsKey(gid))
                                  .findAny().orElseThrow(AssertionError::new);
            assertEquals(current.groupInfo().get(newGroup).getRight().size(),
                    numMoved);
            assertEquals(numShards / current.groupInfo().size(), numMoved);
        } else if (currentNumGroups < previousNumGroups) {
            int removedGroup = previous.groupInfo().keySet().stream()
                                       .filter(gid -> !current.groupInfo()
                                                              .containsKey(gid))
                                       .findAny()
                                       .orElseThrow(AssertionError::new);
            assertEquals(
                    previous.groupInfo().get(removedGroup).getRight().size(),
                    numMoved);
        } else {
            // Must have been a move operation
            assertEquals(1, numMoved);
        }

    }

    private void checkShardMovement(ShardConfig previous, ShardConfig current) {
        checkShardMovement(previous, current, DEFAULT_NUM_SHARDS);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Commands return OK")
    @TestPointValue(5)
    public void test01commandsReturnOk() {
        Result result = execute(new Join(1, group(1)));
        assertEquals(new Ok(), result);

        result = execute(new Join(2, group(2)));
        assertEquals(new Ok(), result);

        ShardConfig config = getLatest(false);
        int shardToMove =
                config.groupInfo().get(1).getRight().stream().findAny()
                      .orElseThrow(() -> new AssertionError(
                              "Group 1 has no shards mapped to it"));

        result = execute(new Move(2, shardToMove));
        assertEquals(new Ok(), result);

        result = execute(new Leave(2));
        assertEquals(new Ok(), result);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Initial query returns NO_CONFIG")
    @TestPointValue(5)
    public void test02initialQueryReturnsNoConfig() {
        Result result = execute(new Query(-1));
        assertEquals(new Error(), result);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Bad commands return ERROR")
    @TestPointValue(5)
    public void test03CommandsReturnError() {
        execute(new Join(1, group(1)));
        Result result = execute(new Join(1, group(1)));
        assertEquals(new Error(), result);

        result = execute(new Leave(2));
        assertEquals(new Error(), result);

        execute(new Join(2, group(2)));

        ShardConfig config = getLatest(false);
        int shardToMove =
                config.groupInfo().get(1).getRight().stream().findAny()
                      .orElseThrow(() -> new AssertionError(
                              "Group 1 has no shards mapped to it"));

        result = execute(new Move(1, shardToMove));
        assertEquals(new Error(), result);

        result = execute(new Move(3, shardToMove));
        assertEquals(new Error(), result);

        result = execute(new Move(2, 0));
        assertEquals(new Error(), result);

        result = execute(new Move(2, DEFAULT_NUM_SHARDS + 1));
        assertEquals(new Error(), result);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Initial config correct")
    @TestPointValue(5)
    public void test04InitialConfigCorrect() {
        execute(new Join(1, group(1)));
        Map<Integer, Pair<Set<Address>, Set<Integer>>> expected =
                new HashMap<>();
        expected.put(1, new ImmutablePair<>(group(1),
                fullShardRange(DEFAULT_NUM_SHARDS)));
        ShardConfig received = getLatest(true);
        assertEquals(new ShardConfig(INITIAL_CONFIG_NUM, expected), received);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Basic join/leave")
    @TestPointValue(5)
    public void test05BasicJoinLeave() {
        execute(new Join(1, group(1)));
        ShardConfig previous = getLatest(true);
        checkConfig(previous, 1);

        execute(new Join(2, group(2)));
        ShardConfig next = getLatest(true);
        checkConfig(next, 1, 2);
        checkShardMovement(previous, next);
        previous = next;

        execute(new Join(3, group(3)));
        next = getLatest(true);
        checkConfig(next, 1, 2, 3);
        checkShardMovement(previous, next);
        previous = next;

        execute(new Leave(3));
        next = getLatest(true);
        checkConfig(next, 1, 2);
        checkShardMovement(previous, next);
        previous = next;

        execute(new Leave(2));
        next = getLatest(true);
        checkConfig(next, 1);
        checkShardMovement(previous, next);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Historical queries")
    @TestPointValue(5)
    public void test06HistoricalQueries() {
        test05BasicJoinLeave();
        for (int i = 0; i < 5; i++) {
            getConfig(INITIAL_CONFIG_NUM + i, false, true);
        }
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Move command")
    @TestPointValue(5)
    public void test07MoveShards() {
        execute(new Join(1, group(1)));
        execute(new Join(2, group(2)));
        ShardConfig config = getLatest(false);

        Set<Integer> groupOneShards = config.groupInfo().get(1).getRight();
        assertTrue(groupOneShards.size() == 5);

        Set<Integer> remaining = new HashSet<>(groupOneShards);
        for (Integer shard : groupOneShards) {
            execute(new Move(2, shard));
            remaining.remove(shard);
            config = getLatest(true);
            checkConfig(groupOneShards.size() - remaining.size(),
                    DEFAULT_NUM_SHARDS, config, 1, 2);
            assertEquals(remaining, config.groupInfo().get(1).getRight());
        }

        execute(new Join(3, group(3)));
        ShardConfig next = getLatest(true);
        checkConfig(next, 1, 2, 3);
    }

    @Test(timeout = 5 * 1000)
    @PrettyTestName("Application deterministic")
    @TestPointValue(10)
    public void test08Determinism() {
        for (int i = 0; i < 10; i++) {
            shardMaster = new ShardMaster(100);

            execute(new Join(1, group(1)));
            ShardConfig config = getConfig(-1, false, false);
            checkConfig(0, 100, config, 1);

            execute(new Join(2, group(2)));
            config = getConfig(-1, false, false);
            checkConfig(0, 100, config, 1, 2);

            execute(new Join(3, group(3)));
            config = getConfig(-1, false, false);
            checkConfig(0, 100, config, 1, 2, 3);

            execute(new Leave(3));
            config = getConfig(-1, false, false);
            checkConfig(0, 100, config, 1, 2);

            List<Integer> groupOneShards =
                    new ArrayList<>(config.groupInfo().get(1).getRight());
            Collections.sort(groupOneShards);
            assertTrue(groupOneShards.size() == 50);

            for (int j = 0; j < 10; j++) {
                execute(new Move(2, groupOneShards.get(j)));
                config = getConfig(-1, false, false);
                checkConfig(j + 1, 100, config, 1, 2);
            }

            execute(new Join(3, group(3)));
            getConfig(-1, false, false);
        }
    }
}
