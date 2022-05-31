/*
 * Copyright (c) 2020 Ellis Michael (emichael@cs.washington.edu)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package dslabs.framework.testing.utils;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Client;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Result;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.LocalAddress;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.StateGenerator;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.StatePredicate.PredicateResult;
import dslabs.framework.testing.Workload;
import dslabs.framework.testing.runner.RunState;
import dslabs.framework.testing.search.SerializableTrace;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CloningTest {
    @SuppressWarnings("unchecked")
    private static Set<Class<?>> getCannotClone()
            throws NoSuchFieldException, IllegalAccessException {
        Field cannotCloneField = Cloning.class.getDeclaredField("cannotClone");
        cannotCloneField.setAccessible(true);
        return (Set<Class<?>>) cannotCloneField.get(null);
    }

    @Before
    public void clearCannotClone()
            throws IllegalAccessException, NoSuchFieldException {
        getCannotClone().clear();
    }

    private static void assertFastCloned()
            throws NoSuchFieldException, IllegalAccessException {
        assertTrue(getCannotClone().isEmpty());
    }

    private static void assertNotFastCloned()
            throws NoSuchFieldException, IllegalAccessException {
        assertFalse(getCannotClone().isEmpty());
    }

    private void clonesEqual(Serializable obj) {
        assertEquals(obj, Cloning.clone(obj));
    }

    private void serializeDeserializeEqual(Serializable obj) {
        assertEquals(obj, Cloning.deserialize(Cloning.serialize(obj)));
    }

    @Test
    public void equalsValid() {
        assertNotEquals(new NodeExample(new AddressExample("foo"), "bar"),
                new NodeExample(new AddressExample("foo"), "baz"));
        assertNotEquals(new AddressExample("foo"), new AddressExample("bar"));
        assertNotEquals(new ApplicationExample("foo", 1),
                new ApplicationExample("foo", 2));
        assertNotEquals(new ApplicationExample("foo", 1),
                new ApplicationExample("bar", 1));
        assertNotEquals(new MessageExample("foo", true),
                new MessageExample("bar", true));
        assertNotEquals(new MessageExample("foo", true),
                new MessageExample("foo", false));
        assertNotEquals(Address.subAddress(new AddressExample("foo"), "bar"),
                Address.subAddress(new AddressExample("foo"), "baz"));
        assertNotEquals(Address.subAddress(new AddressExample("foo"), "bar"),
                Address.subAddress(new AddressExample("baz"), "bar"));
    }

    @Test
    public void cloning() {
        clonesEqual(new NodeExample(new AddressExample("foo"), "bar"));
        clonesEqual(new AddressExample("foo"));
        clonesEqual(new ApplicationExample("foo", 5));
        clonesEqual(new MessageExample("foo", true));
        clonesEqual(new MessageExample("foo", false));
        clonesEqual(Address.subAddress(new AddressExample("foo"), "bar"));

        clonesEqual(new AlsoNotFastSerializable());
        clonesEqual(new ShouldFastSerialize());
    }

    @Test
    public void serializationDeserialization() {
        serializeDeserializeEqual(
                new NodeExample(new AddressExample("foo"), "bar"));
        serializeDeserializeEqual(new AddressExample("foo"));
        serializeDeserializeEqual(new ApplicationExample("foo", 5));
        serializeDeserializeEqual(new MessageExample("foo", true));
        serializeDeserializeEqual(new MessageExample("foo", false));
        serializeDeserializeEqual(
                Address.subAddress(new AddressExample("foo"), "bar"));

        serializeDeserializeEqual(new AlsoNotFastSerializable());
    }

    @Test
    public void fastCloneWorks()
            throws NoSuchFieldException, IllegalAccessException {
        Cloning.clone(new NodeExample(new AddressExample("foo"), "bar"));
        Cloning.clone(new AddressExample("foo"));
        Cloning.clone(new ApplicationExample("foo", 5));
        Cloning.clone(new MessageExample("foo", true));
        Cloning.clone(new MessageExample("foo", false));
        Cloning.clone(Address.subAddress(new AddressExample("foo"), "bar"));

        assertFastCloned();

        Cloning.clone(new ShouldFastSerialize());
        assertFastCloned();
    }

    @Test
    public void notFastSerializable()
            throws NoSuchFieldException, IllegalAccessException {
        Cloning.clone(new NotFastSerializable());
        assertNotFastCloned();

        clearCannotClone();

        Cloning.clone(new AlsoNotFastSerializable());
        assertNotFastCloned();
    }

    @Test(expected = org.apache.commons.lang3.SerializationException.class)
    public void notSerializableFails() {
        Cloning.clone(new NotSerializable());
    }

    @Test
    public void fastCloneKeySet()
            throws NoSuchFieldException, IllegalAccessException {
        ShouldFastSerialize s = new ShouldFastSerialize();

        HashMap<String, Integer> foo = new HashMap<>();
        foo.put("foo", 1);
        foo.put("bar", 2);
        s.foo = foo.keySet();
        Set<String> setClone = Cloning.clone(s).foo;
        assertEquals(s.foo, setClone);

        // Check that a cloned keySet is actually a deep clone
        foo.put("baz", 3);
        assertNotEquals(s.foo, setClone);

        assertFastCloned();
    }

    @Test
    public void fastCloneValues()
            throws NoSuchFieldException, IllegalAccessException {
        AlsoShouldFastSerialize s = new AlsoShouldFastSerialize();
        AlsoShouldFastSerialize s2 = Cloning.clone(s);
        assertNotEquals(s, s2); // values collections don't implement .equals
        assertFastCloned();

        HashMap<Integer, String> foo = new HashMap<>();
        foo.put(1, "foo");
        foo.put(2, "bar");
        s.foo = foo.values();

        Collection<String> valuesClone = Cloning.clone(s).foo;
        assertNotEquals(s.foo, valuesClone); // same thing
        // poor man's collection.equals
        assertTrue(s.foo.containsAll(valuesClone) &&
                valuesClone.containsAll(s.foo));
        assertFastCloned();

        // Check that a cloned keySet is actually a deep clone
        foo.put(3, "baz");
        assertFalse(s.foo.containsAll(valuesClone) &&
                valuesClone.containsAll(s.foo));
    }

    @Test
    public void cloneRandom()
            throws NoSuchFieldException, IllegalAccessException {
        Random r1 = new Random();
        Random r2 = Cloning.clone(r1);
        assertNotEquals(r1, r2); // random uses default .equals
        assertFastCloned();

        // check that they actually produce the same results
        for (int i = 0; i < 100; i++) {
            assertEquals(r1.nextInt(), r2.nextInt());
        }
    }

    @Test
    public void cloneHashMultimap()
            throws NoSuchFieldException, IllegalAccessException {
        HashMultimap<Integer, String> h1 = HashMultimap.create();
        h1.put(1, "foo");
        h1.put(1, "bar");
        h1.put(2, "baz");
        HashMultimap<Integer, String> h2 = Cloning.clone(h1);
        assertEquals(h1, h2);
        assertFastCloned();

        h1.put(1, "foo");
        assertEquals(h1, h2);

        h1.put(1, "foo2");
        assertNotEquals(h1, h2);
    }

    @Test
    public void cloneArrayListMultimap()
            throws NoSuchFieldException, IllegalAccessException {
        ArrayListMultimap<Integer, String> h1 = ArrayListMultimap.create();
        h1.put(1, "foo");
        h1.put(1, "bar");
        h1.put(2, "baz");
        ArrayListMultimap<Integer, String> h2 = Cloning.clone(h1);
        assertEquals(h1, h2);
        assertFastCloned();

        h1.put(1, "foo");
        assertNotEquals(h1, h2);

        h1.put(1, "foo2");
        assertNotEquals(h1, h2);
    }

    @Test
    public void linkedHashMultiMap()
            throws NoSuchFieldException, IllegalAccessException {
        LinkedHashMultimap<Integer, String> h1 = LinkedHashMultimap.create();
        h1.put(1, "foo");
        h1.put(1, "bar");
        h1.put(2, "baz");
        LinkedHashMultimap<Integer, String> h2 = Cloning.clone(h1);
        assertEquals(h1, h2);
        assertFastCloned();

        h1.put(1, "foo");
        assertEquals(h1, h2);

        h1.put(1, "foo2");
        assertNotEquals(h1, h2);
    }

    @Test
    public void treeMap() throws NoSuchFieldException, IllegalAccessException {
        TreeMap<Integer, String> m1 = new TreeMap<>();
        m1.put(1, "foo");
        m1.put(1, "bar");
        m1.put(2, "baz");
        TreeMap<Integer, String> m2 = Cloning.clone(m1);
        assertEquals(m1, m2);
        assertFastCloned();
    }

    @Test
    public void bitSet() throws NoSuchFieldException, IllegalAccessException {
        BitSet b = new BitSet();
        b.set(1);
        b.set(3);

        BitSet b2 = Cloning.clone(b);
        assertEquals(b, b2);
        assertFastCloned();

        b2.set(1);
        assertEquals(b, b2);

        b2.set(0);
        assertNotEquals(b, b2);
    }

    @Test
    public void transientFinal()
            throws NoSuchFieldException, IllegalAccessException {
        TransientFinalExample o = new TransientFinalExample("foobar");
        TransientFinalExample o2 = Cloning.clone(o);
        assertFastCloned();
        assertNull(o2.foo);
    }

    private void testStatePredicate(StatePredicate s1) {
        StatePredicate s2 = Cloning.deserialize(Cloning.serialize(s1));
        assertEquals(s1.toString(), s2.toString());
        assertEquals(s1.name(), s2.name());
        var s = new RunState(StateGenerator.builder().serverSupplier(a -> null)
                                           .clientSupplier(ClientExample::new)
                                           .workloadSupplier(new Workload() {
                                               @Override
                                               public Pair<Command, Result> nextCommandAndResult(
                                                       Address clientAddress) {
                                                   return null;
                                               }

                                               @Override
                                               public boolean hasNext() {
                                                   return false;
                                               }

                                               @Override
                                               public boolean hasResults() {
                                                   return false;
                                               }

                                               @Override
                                               public void add(
                                                       Command command) {

                                               }

                                               @Override
                                               public void add(Command command,
                                                               Result result) {

                                               }

                                               @Override
                                               public void reset() {

                                               }

                                               @Override
                                               public int size() {
                                                   return 0;
                                               }

                                               @Override
                                               public boolean infinite() {
                                                   return false;
                                               }
                                           }).build());
        s.addClientWorker(new LocalAddress("foo"));
        PredicateResult r1 = s1.test(s);
        PredicateResult r2 = s2.test(s);
        assertEquals(r1.exceptionThrown(), r2.exceptionThrown());
        assertEquals(r1.value(), r2.value());
        assertEquals(r1.detail(), r2.detail());
        assertEquals(r1.errorMessage(), r2.errorMessage());
    }

    @Test
    public void predicatesSerialize() {
        testStatePredicate(StatePredicate.RESULTS_OK);
        testStatePredicate(StatePredicate.CLIENTS_DONE);
        testStatePredicate(StatePredicate.clientDone(new LocalAddress("foo")));
        testStatePredicate(
                StatePredicate.clientHasResults(new LocalAddress("foo"), 3));
        testStatePredicate(StatePredicate.NONE_DECIDED);
        testStatePredicate(StatePredicate.ALL_RESULTS_SAME);
        testStatePredicate(new StatePredicate("totally a test",
                s -> new ImmutablePair<>(true, "bar")));
    }

    @Test
    public void serializableTraces()
            throws InvocationTargetException, InstantiationException,
            IllegalAccessException {
        List<Event> es = new ArrayList<>();
        es.add(new Event(new MessageEnvelope(new LocalAddress("foo"),
                new AddressExample("bar"), new MessageExample("asdf", false))));
        es.add(new Event(new MessageEnvelope(new LocalAddress("asdf"),
                new AddressExample("1234"), new MessageExample("baz", false))));

        var con = SerializableTrace.class.getDeclaredConstructors()[0];
        con.setAccessible(true);

        SerializableTrace t1 = (SerializableTrace) con.newInstance(es,
                List.of(StatePredicate.RESULTS_OK,
                        StatePredicate.ALL_RESULTS_SAME),
                StateGenerator.builder().serverSupplier(a -> null)
                              .clientSupplier(ClientExample::new)
                              .workloadSupplier(a -> null).build(),
                List.of(new LocalAddress("foo")), Collections.emptyList(),
                "lab-foo", 2, "FooClass", "fooMethod");

        SerializableTrace t2 = Cloning.deserialize(Cloning.serialize(t1));

        assertEquals(t1.history(), t2.history());
        assertEquals(t1.invariants().size(), t2.invariants().size());
        assertEquals(t1.servers(), t2.servers());
        assertEquals(t1.clientWorkers(), t2.clientWorkers());
        assertEquals(t1.labId(), t2.labId());
        assertEquals(t1.labPart(), t2.labPart());
        assertEquals(t1.testClassName(), t2.testClassName());
        assertEquals(t1.testMethodName(), t2.testMethodName());
        assertEquals(t1.createdDate(), t2.createdDate());
        assertEquals(t1.stateGenerator().client(new LocalAddress("foo")),
                t2.stateGenerator().client(new LocalAddress("foo")));
    }
}

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class NodeExample extends Node {
    private final String s;

    NodeExample(Address a, String s) {
        super(a);
        this.s = s;
    }

    @Override
    public void init() {

    }
}

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class ClientExample extends Node implements Client {

    protected ClientExample(@NonNull Address address) {
        super(address);
    }

    @Override
    public void sendCommand(Command command) {

    }

    @Override
    public boolean hasResult() {
        return false;
    }

    @Override
    public Result getResult() throws InterruptedException {
        return null;
    }

    @Override
    public void init() {

    }
}

@Data
class AddressExample implements Address {
    private final String s;

    @Override
    public int compareTo(Address o) {
        return 0;
    }
}

@Data
class ApplicationExample implements Application {
    private final String s;

    private final int i;

    @Override
    public Result execute(Command command) {
        return null;
    }

}

@Data
class MessageExample implements Message {
    private final String s;
    private final boolean b;
}

@EqualsAndHashCode(of = "a")
class NotFastSerializable implements Serializable {
    transient int a = 5;
}

@EqualsAndHashCode(of = "a")
class AlsoNotFastSerializable implements Serializable {
    transient final int a = 5;
}

@EqualsAndHashCode
class ShouldFastSerialize implements Serializable {
    Set<String> foo;

    ShouldFastSerialize() {
        HashMap<String, Integer> bar = new HashMap<>();
        bar.put("foo", 1);
        bar.put("bar", 2);
        foo = bar.keySet();
    }
}

@EqualsAndHashCode
class AlsoShouldFastSerialize implements Serializable {
    Collection<String> foo;

    AlsoShouldFastSerialize() {
        HashMap<Integer, String> bar = new HashMap<>();
        bar.put(1, "foo");
        bar.put(2, "bar");
        foo = bar.values();
    }
}

@AllArgsConstructor
class TransientFinalExample implements Serializable {
    transient final String foo;
}

@EqualsAndHashCode
class NotSerializable implements Serializable {
    final Set<String> foo;
    transient int a = 5;

    NotSerializable() {
        HashMap<String, Integer> bar = new HashMap<>();
        bar.put("foo", 1);
        bar.put("bar", 2);
        foo = bar.keySet();
    }
}
