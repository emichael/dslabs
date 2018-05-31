package dslabs.kvstore;

import dslabs.framework.Address;
import dslabs.framework.Command;
import dslabs.framework.Result;
import dslabs.framework.testing.InfiniteWorkload;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.utils.SerializableFunction;
import dslabs.kvstore.KVStore.Append;
import dslabs.kvstore.KVStore.AppendResult;
import dslabs.kvstore.KVStore.Get;
import dslabs.kvstore.KVStore.GetResult;
import dslabs.kvstore.KVStore.KVStoreCommand;
import dslabs.kvstore.KVStore.KVStoreResult;
import dslabs.kvstore.KVStore.KeyNotFound;
import dslabs.kvstore.KVStore.Put;
import dslabs.kvstore.KVStore.PutOk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import static dslabs.framework.testing.StatePredicate.resultsPredicate;

public abstract class KVStoreWorkload extends Workload {
    public static final String OK = "Ok", KEY_NOT_FOUND = "KeyNotFound";

    public static Get get(Object key) {
        return new Get(key.toString());
    }

    public static Put put(Object key, Object value) {
        return new Put(key.toString(), value.toString());
    }

    public static Append append(Object key, Object value) {
        return new Append(key.toString(), value.toString());
    }

    public static GetResult getResult(Object value) {
        return new GetResult(value.toString());
    }

    public static KeyNotFound keyNotFound() {
        return new KeyNotFound();
    }

    public static PutOk putOk() {
        return new PutOk();
    }

    public static AppendResult appendResult(Object value) {
        return new AppendResult(value.toString());
    }

    public static class KVStoreParser implements
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

        String[] split = c.split(":", 3);

        KVStoreCommand command;
        KVStoreResult result = null;

        switch (split[0]) {
            case "GET":
                if (split.length == 1) {
                    // TODO: throw exception?
                    return null;
                }
                String key;
                if (split.length == 2) {
                    key = split[1];
                } else {
                    key = split[1] + split[2];
                }
                command = get(key);
                if (r != null) {
                    if (r.equals(KEY_NOT_FOUND)) {
                        result = keyNotFound();
                    } else {
                        result = getResult(r);
                    }
                }
                break;

            case "PUT":
                if (split.length != 3) {
                    return null;
                }
                command = put(split[1], split[2]);
                if (r != null && r.equals(OK)) {
                    // TODO: throw error if r not OK?
                    result = putOk();
                }
                break;

            case "APPEND":
                if (split.length != 3) {
                    return null;
                }
                command = append(split[1], split[2]);
                if (r != null) {
                    result = appendResult(r);
                }
                break;

            default:
                return null;
        }

        return new ImmutablePair<>(command, result);
    }

    public static WorkloadBuilder builder() {
        return Workload.builder().parser(new KVStoreParser());
    }

    public static Workload emptyWorkload() {
        return builder().commands().build();
    }

    public static Workload workload(String... commandStrings) {
        return builder().commandStrings(commandStrings).build();
    }


    /* KVStore-specific workloads */

    public static final Workload simpleWorkload = builder()
            .commands(put("key1", "v1a"), get("key1"), put("key2", "v2a"),
                    get("key2"), put("key1", "v1b"), get("key1"),
                    append("key3", "v3a"), put("key3", "v3b"),
                    append("key3", "v3c"), append("key3", "v3d"),
                    append("key4", "v4"), append("key4", "v4"), get("key4"),
                    get("key5"))
            .results(putOk(), getResult("v1a"), putOk(), getResult("v2a"),
                    putOk(), getResult("v1b"), appendResult("v3a"), putOk(),
                    appendResult("v3bv3c"), appendResult("v3bv3cv3d"),
                    appendResult("v4"), appendResult("v4v4"), getResult("v4v4"),
                    keyNotFound()).build();

    public static final Workload putAppendGetWorkload = builder()
            .commands(put("foo", "bar"), append("foo", "baz"), get("foo"))
            .results(putOk(), appendResult("barbaz"), getResult("barbaz"))
            .build();

    public static final Workload appendAppendGet = builder()
            .commands(append("foo", "bar"), append("foo", "bar"), get("foo"))
            .results(appendResult("bar"), appendResult("barbar"),
                    getResult("barbar")).build();

    public static final Workload putGetWorkload =
            builder().commands(put("foo", "bar"), get("foo"))
                     .results(putOk(), getResult("bar")).build();

    public static final Workload putWorkload =
            builder().commands(put("foo", "bar")).results(putOk()).build();

    public static Workload appendDifferentKeyWorkload(int numRounds) {
        List<String> workload = new ArrayList<>();
        List<String> results = new ArrayList<>();

        for (int i = 0; i < numRounds; i++) {
            workload.add("APPEND:KEY-%a:" + i);
            results.add((i > 0 ? results.get(i - 1) : "") + i);
        }

        // TODO: add methods for lists
        return builder().commandStrings(workload.toArray(new String[0]))
                        .resultStrings(results.toArray(new String[0])).build();
    }

    public static Workload appendSameKeyWorkload(int numRounds) {
        return builder().commandStrings("APPEND:foo:%a,%i").numTimes(numRounds)
                        .build();
    }

    private static class DifferentKeysInfiniteWorkload
            extends InfiniteWorkload {
        private final Map<String, String> data = new HashMap<>();
        private boolean lastWasGet = true;
        private String lastPutKey = null;

        @Getter private final int millisBetweenRequests;

        DifferentKeysInfiniteWorkload(int millisBetweenRequests) {
            this.millisBetweenRequests = millisBetweenRequests;
        }

        DifferentKeysInfiniteWorkload() {
            this(0);
        }

        @Override
        public Pair<Command, Result> nextCommandAndResult(
                Address clientAddress) {
            Random rand = new Random();

            if (lastWasGet) {
                lastPutKey =
                        clientAddress.toString() + "-" + (rand.nextInt(5) + 1);
                String v = RandomStringUtils.randomAlphanumeric(8);
                data.put(lastPutKey, v);
                lastWasGet = false;
                return new ImmutablePair<>(put(lastPutKey, v), putOk());
            } else {
                lastWasGet = true;
                return new ImmutablePair<>(get(lastPutKey),
                        getResult(data.get(lastPutKey)));
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
            lastPutKey = null;
        }
    }

    // TODO: make anonymous class just because
    public static final Workload differentKeysInfiniteWorkload =
            new DifferentKeysInfiniteWorkload();

    public static Workload differentKeysInfiniteWorkload(
            int millisBetweenRequests) {
        return new DifferentKeysInfiniteWorkload(millisBetweenRequests);
    }


    /* KVStore-specific predicates */

    private static boolean listInStrictPrefixOrder(List<String> l) {
        for (int i = 0; i < l.size() - 1; i++) {
            if (!l.get(i + 1).startsWith(l.get(i)) ||
                    l.get(i + 1).equals(l.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static final StatePredicate APPENDS_LINEARIZABLE = resultsPredicate(
            "Sequence of appends to the same key is linearizable", rs -> {
                List<String> allResults = new ArrayList<>();

                // TODO: clean this up
                for (List<Result> r : rs) {
                    List<String> rp = new ArrayList<>();
                    for (Result ar : r) {
                        if (!(ar instanceof AppendResult)) {
                            return false;
                        }
                        rp.add(((AppendResult) ar).value());
                    }

                    if (!listInStrictPrefixOrder(rp)) {
                        return false;
                    }
                    allResults.addAll(rp);
                }
                allResults.sort(Comparator.comparingInt(String::length));
                return listInStrictPrefixOrder(allResults);
            });
}
