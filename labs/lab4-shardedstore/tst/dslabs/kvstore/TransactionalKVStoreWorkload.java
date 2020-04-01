package dslabs.kvstore;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Result;
import dslabs.framework.testing.InfiniteWorkload;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.utils.SerializableFunction;
import dslabs.kvstore.KVStore.KVStoreResult;
import dslabs.kvstore.TransactionalKVStore.MultiGet;
import dslabs.kvstore.TransactionalKVStore.MultiGetResult;
import dslabs.kvstore.TransactionalKVStore.MultiPut;
import dslabs.kvstore.TransactionalKVStore.MultiPutOk;
import dslabs.kvstore.TransactionalKVStore.Swap;
import dslabs.kvstore.TransactionalKVStore.SwapOk;
import dslabs.kvstore.TransactionalKVStore.Transaction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.NonNull;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import static dslabs.framework.testing.StatePredicate.resultsPredicate;

public abstract class TransactionalKVStoreWorkload extends KVStoreWorkload {
    public static MultiGet multiGet(@NonNull Object... keys) {
        return new MultiGet(Arrays.stream(keys).map(Object::toString)
                                  .collect(Collectors.toSet()));
    }

    public static MultiGet multiGet(@NonNull Set<String> keys) {
        return new MultiGet(Sets.newHashSet(keys));
    }

    public static MultiPut multiPut(@NonNull Object... values) {
        if (values.length == 0 || values.length % 2 != 0) {
            throw new IllegalArgumentException();
        }

        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            m.put(values[i].toString(), values[i + 1].toString());
        }

        return new MultiPut(m);
    }

    public static MultiPut multiPut(@NonNull Map<String, String> values) {
        return new MultiPut(Maps.newHashMap(values));
    }

    public static Swap swap(Object key1, Object key2) {
        return new Swap(key1.toString(), key2.toString());
    }

    public static MultiGetResult multiGetResult(@NonNull Object... values) {
        if (values.length == 0 || values.length % 2 != 0) {
            throw new IllegalArgumentException();
        }

        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            m.put(values[i].toString(), values[i + 1].toString());
        }

        return new MultiGetResult(m);
    }

    public static MultiGetResult multiGetResult(
            @NonNull Map<String, String> values) {
        return new MultiGetResult(Maps.newHashMap(values));
    }

    public static MultiPutOk multiPutOk() {
        return new MultiPutOk();
    }

    public static SwapOk swapOk() {
        return new SwapOk();
    }

    public static class TransactionalKVStoreParser implements
            SerializableFunction<Pair<String, String>, Pair<Command, Result>> {
        @Override
        public Pair<Command, Result> apply(
                Pair<String, String> commandAndResultString) {
            return parse(commandAndResultString);
        }
    }

    public static Pair<Command, Result> parse(
            @NonNull Pair<String, String> commandAndResultString) {
        String c = commandAndResultString.getLeft();
        String r = commandAndResultString.getRight();

        String[] split = c.split(":", 2);

        if (split.length == 1) {
            // TODO: throw exception instead of returning null everywhere?
            return null;
        }

        Transaction command;
        KVStoreResult result = null;

        switch (split[0]) {
            case "MULTIGET":
                String[] keys = split[1].split(":");
                command = multiGet((Object[]) keys);
                if (r != null) {
                    String[] values = r.split(":");
                    if (keys.length != values.length) {
                        return null;
                    }
                    Map<String, String> rp = new HashMap<>();
                    for (int i = 0; i < keys.length; i++) {
                        rp.put(keys[i], values[i]);
                    }
                    result = new MultiGetResult(rp);
                }
                break;

            case "MULTIPUT":
                command = multiPut((Object[]) split[1].split(":"));
                if (r != null && r.equals(OK)) {
                    // TODO: throw error if r not OK?
                    result = multiPutOk();
                }
                break;

            case "SWAP":
                keys = split[1].split(":", 2);
                if (keys.length != 2) {
                    return null;
                }
                command = swap(keys[0], keys[1]);
                if (r != null && r.equals(OK)) {
                    // TODO: throw error if r not OK?
                    result = swapOk();
                }
                break;

            default:
                return KVStoreWorkload.parse(commandAndResultString);
        }

        return new ImmutablePair<>(command, result);
    }

    public static WorkloadBuilder builder() {
        return Workload.builder().parser(new TransactionalKVStoreParser());
    }

    public static Workload emptyWorkload() {
        return builder().commands().build();
    }

    public static Workload workload(String... commandStrings) {
        return builder().commandStrings(commandStrings).build();
    }

    /* TransactionalKVStore-specific workloads */
    public static final Workload simpleWorkload = builder()
            .commands(multiPut("key1-1", "foo1", "key1-2", "foo2"),
                    multiGet("key1-1", "key1-2"), append("key1-1", "bar1"),
                    append("key1-2", "bar2"), multiGet("key1-1", "key1-2"),
                    swap("key1-1", "key1-2"), multiGet("key1-1", "key1-2"),
                    put("key2-1", "baz1"), put("key2-2", "baz2"),
                    multiGet("key2-1", "key2-2"),
                    multiGet("key1-1", "key2-1", "key3-1"))
            .results(multiPutOk(),
                    multiGetResult("key1-1", "foo1", "key1-2", "foo2"),
                    appendResult("foo1bar1"), appendResult("foo2bar2"),
                    multiGetResult("key1-1", "foo1bar1", "key1-2", "foo2bar2"),
                    swapOk(),
                    multiGetResult("key1-1", "foo2bar2", "key1-2", "foo1bar1"),
                    putOk(), putOk(),
                    multiGetResult("key2-1", "baz1", "key2-2", "baz2"),
                    multiGetResult("key1-1", "foo2bar2", "key2-1", "baz1",
                            "key3-1", MultiGetResult.KEY_NOT_FOUND)).build();

    public static final Workload putGetWorkload = builder()
            .commands(multiPut("key1", "foo1", "key2", "foo2"),
                    multiGet("key1", "key2")).results(multiPutOk(),
                    multiGetResult("key1", "foo1", "key2", "foo2")).build();

    private static class DifferentKeysInfiniteWorkload
            extends InfiniteWorkload {
        private final Random rand = new Random();
        private final int numShards;
        private final List<Integer> shardNums;

        private final Map<String, String> data = new HashMap<>();
        private boolean lastWasGet = true;

        DifferentKeysInfiniteWorkload(int numShards) {
            this.numShards = numShards;
            this.shardNums = IntStream.rangeClosed(1, numShards).boxed()
                                      .collect(Collectors.toList());
        }

        @Override
        public Pair<Command, Result> nextCommandAndResult(
                Address clientAddress) {
            // Randomly choose the key set
            Set<String> keys = new HashSet<>();
            Collections.shuffle(shardNums);
            int numKeys = rand.nextInt(numShards) + 1;
            for (int i = 0; i < numKeys; i++) {
                keys.add(String.format("key-%s-%s", clientAddress,
                        shardNums.get(i)));
            }

            if (lastWasGet) {
                Map<String, String> puts = new HashMap<>();
                keys.forEach(s -> puts.put(s, RandomStringUtils
                        .random(8, 0, 0, true, true, null, rand)));
                data.putAll(puts);
                lastWasGet = false;
                return new ImmutablePair<>(multiPut(puts), multiPutOk());
            } else {
                Map<String, String> values = new HashMap<>();
                keys.forEach(k -> values.put(k,
                        data.getOrDefault(k, MultiGetResult.KEY_NOT_FOUND)));
                lastWasGet = true;
                return new ImmutablePair<>(multiGet(keys),
                        multiGetResult(values));
            }
        }

        @Override
        public boolean hasResults() {
            return true;
        }

        @Override
        public void reset() {
            data.clear();
            lastWasGet = true;
        }
    }

    public static Workload differentKeysInfiniteWorkload(int numShards) {
        return new DifferentKeysInfiniteWorkload(numShards);
    }


    /* TransactionalKVStore-specific predicates */
    public static final StatePredicate MULTI_GETS_MATCH =
            resultsPredicate("Multi-get returns same values for all keys",
                    rs -> {
                        for (List<Result> r : rs) {
                            for (Result result : r) {
                                if (!(result instanceof MultiGetResult)) {
                                    continue;
                                }
                                MultiGetResult mgr = (MultiGetResult) result;
                                if (mgr.values().values().stream().distinct()
                                       .count() != 1) {
                                    return false;
                                }
                            }
                        }
                        return true;
                    });
}
