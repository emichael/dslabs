package dslabs.kvstore;

import com.google.common.collect.Sets;
import dslabs.framework.Command;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TransactionalKVStore extends KVStore {
    /**
     * A simple, single-round transaction (i.e., a transaction whose read and
     * write sets are known a priori).
     */
    public interface Transaction extends KVStoreCommand {
        Set<String> readSet();

        Set<String> writeSet();

        default Set<String> keySet() {
            return Sets.union(readSet(), writeSet());
        }

        /**
         * Takes a map that holds the keys and values of all keys in readSet and
         * writeSet that currently have values, writes the resulting values to
         * the keys in writeSet (potentially deleting keys), returns the
         * result.
         *
         * @param db
         *         a map holding the current values of all keys in keySet
         * @return the result of the transaction
         */
        KVStoreResult run(Map<String, String> db);

        @Override
        default boolean readOnly() {
            return writeSet().isEmpty();
        }
    }

    @Data
    public static final class MultiGet implements Transaction {
        @NonNull private final Set<String> keys;

        @Override
        public Set<String> readSet() {
            return keys;
        }

        @Override
        public Set<String> writeSet() {
            return Collections.emptySet();
        }

        @Override
        public KVStoreResult run(Map<String, String> db) {
            Map<String, String> result = new HashMap<>();
            for (String key : keys) {
                result.put(key,
                        db.getOrDefault(key, MultiGetResult.KEY_NOT_FOUND));
            }
            return new MultiGetResult(result);
        }
    }

    @Data
    public static final class MultiPut implements Transaction {
        @NonNull private final Map<String, String> values;

        @Override
        public Set<String> readSet() {
            return Collections.emptySet();
        }

        @Override
        public Set<String> writeSet() {
            return values.keySet();
        }

        @Override
        public KVStoreResult run(Map<String, String> db) {
            db.putAll(values);
            return new MultiPutOk();
        }
    }

    @Data
    public static final class Swap implements Transaction {
        @NonNull private final String key1, key2;

        @Override
        public Set<String> readSet() {
            return Sets.newHashSet(key1, key2);
        }

        @Override
        public Set<String> writeSet() {
            return readSet();
        }

        @Override
        public KVStoreResult run(Map<String, String> db) {
            boolean k1e = db.containsKey(key1), k2e = db.containsKey(key2);

            String v1 = null;
            if (k1e) {
                v1 = db.get(key1);
            }

            if (k2e) {
                db.put(key1, db.get(key2));
            } else {
                db.remove(key1);
            }

            if (k1e) {
                db.put(key2, v1);
            } else {
                db.remove(key2);
            }

            return new SwapOk();
        }
    }

    @Data
    public static final class MultiGetResult implements KVStoreResult {
        public static final String KEY_NOT_FOUND = "KeyNotFound";

        @NonNull private final Map<String, String> values;
    }

    @Data
    public static final class MultiPutOk implements KVStoreResult {
    }

    @Data
    public static final class SwapOk implements KVStoreResult {
    }

    @Override
    public KVStoreResult execute(Command command) {
        if (command instanceof Transaction) {
            Transaction t = (Transaction) command;
            // Your code here...
        }

        return super.execute(command);
    }
}
