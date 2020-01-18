package dslabs.framework.testing.utils;

import dslabs.framework.Address;
import dslabs.framework.Application;
import dslabs.framework.Command;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Result;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CloningTest {
    @Before
    public void clearCannotClone()
            throws IllegalAccessException, NoSuchFieldException {
        Field cannotCloneField = Cloning.class.getDeclaredField("cannotClone");
        cannotCloneField.setAccessible(true);
        Set<Class<?>> cannotClone = (Set<Class<?>>) cannotCloneField.get(null);
        cannotClone.clear();
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

        Field cannotCloneField = Cloning.class.getDeclaredField("cannotClone");
        cannotCloneField.setAccessible(true);
        Set<Class<?>> cannotClone = (Set<Class<?>>) cannotCloneField.get(null);
        assertTrue(cannotClone.isEmpty());

        Cloning.clone(new ShouldFastSerialize());
        assertTrue(cannotClone.isEmpty());
    }

    @Test
    public void notFastSerializable1()
            throws NoSuchFieldException, IllegalAccessException {
        Field cannotCloneField = Cloning.class.getDeclaredField("cannotClone");
        cannotCloneField.setAccessible(true);
        Set<Class<?>> cannotClone = (Set<Class<?>>) cannotCloneField.get(null);
        assertTrue(cannotClone.isEmpty());

        Cloning.clone(new NotFastSerializable());
        assertFalse(cannotClone.isEmpty());
    }

    @Test
    public void notFastSerializable2()
            throws NoSuchFieldException, IllegalAccessException {
        Field cannotCloneField = Cloning.class.getDeclaredField("cannotClone");
        cannotCloneField.setAccessible(true);
        Set<Class<?>> cannotClone = (Set<Class<?>>) cannotCloneField.get(null);
        assertTrue(cannotClone.isEmpty());

        Cloning.clone(new AlsoNotFastSerializable());
        assertFalse(cannotClone.isEmpty());
    }

    @Test(expected = org.apache.commons.lang3.SerializationException.class)
    public void notSerializableFails()
            throws NoSuchFieldException, IllegalAccessException {
        try {
            Cloning.clone(new NotSerializable());
        } catch (Exception e) {
        }
        Field cannotCloneField = Cloning.class.getDeclaredField("cannotClone");
        cannotCloneField.setAccessible(true);
        Set<Class<?>> cannotClone = (Set<Class<?>>) cannotCloneField.get(null);
        assertFalse(cannotClone.isEmpty());

        Cloning.clone(new NotSerializable());
    }

    @Test
    public void fastCloneKeySet() {
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
