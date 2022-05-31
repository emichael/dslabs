/*
 * Copyright (c) 2018 Ellis Michael (emichael@cs.washington.edu)
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

import com.rits.cloning.Cloner;
import com.rits.cloning.ICloningStrategy;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.SerializationUtils;


/*
 * TODO: replace the Immutable annotation with one of my own to allow future
 * replacement of the library. The annotation itself can be annotated with
 * com.rits.cloning.Immutable.
 */

/**
 * Wrapper around serialization utils to allow easy replacement in the future.
 * All cloning, serialization, and deserialization is done with this utility
 * class. Currently, the clone method attempts to use {@link
 * com.rits.cloning.Cloner} for faster cloning through reflection and the use of
 * various fast-cloners. As a fallback, serialization/deserialization is used.
 *
 * <p>The fast cloning library is configured to have the same behavior for
 * transient fields as Java serialization/deserialization - namely, it sets them
 * to null - <b>for all packages beginning with {@code dslabs}</b>. None of the
 * classes in DSLabs should rely on custom serializers or deserializers and make
 * assumptions about transient variables without the same logic being encoded in
 * a fast cloner for the cloning library. Moreover, classes <b>should not
 * contain {@code transient} primitive</b> fields. These cannot be processed by
 * the fast cloning library and will instead be handled by slower serialization
 * and deserialization.
 *
 * <p>The {@link com.rits.cloning.Immutable} annotation can let the fast
 * cloning library know that a particular class is completely immutable and
 * therefore avoid cloning it. That class and all its fields (and all their
 * fields, etc.) must be immutable.
 *
 * <p>Cloning, serialization, and deserialization should be completely
 * transparent to students - aside from the requirement of annotating classes
 * with {@link Serializable}.
 */
public abstract class Cloning {
    private static final Cloner jdclCloner = new Cloner();
    private static final Set<Class<?>> cannotClone =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    static {
        jdclCloner.registerCloningStrategy(new ICloningStrategy() {
            /*
             * This cloning library works for most things. However, since it's
             * not using default serializers, it's only safe to null
             * transient fields declared in this package. Otherwise, ignore this
             * strategy and do whatever the default in the cloning library is.
             */
            @Override
            public Strategy strategyFor(Object toBeCloned, Field field) {
                final int modifiers = field.getModifiers();
                if (Modifier.isTransient(modifiers) &&
                        field.getDeclaringClass().getPackage().getName()
                             .startsWith("dslabs")) {
                    return Strategy.NULL_INSTEAD_OF_CLONE;
                }
                return Strategy.IGNORE;
            }
        });
    }

    private static <T extends Serializable> T jdclClone(T object) {
        return jdclCloner.deepClone(object);
    }

    private static <T extends Serializable> T defaultClone(T object) {
        return SerializationUtils.clone(object);
    }

    private static byte[] defaultSerialize(Serializable object) {
        return SerializationUtils.serialize(object);
    }

    private static <T> T defaultDeserialize(byte[] object) {
        return SerializationUtils.deserialize(object);
    }

    public static <T extends Serializable> T clone(T object) {
        if (object == null) {
            return null;
        }

        T ret;

        if (cannotClone.contains(object.getClass())) {
            ret = defaultClone(object);
        } else {
            try {
                ret = jdclClone(object);
            } catch (Throwable ignored) {
                if (GlobalSettings.doErrorChecks()) {
                    CheckLogger.notFastCloned(object);
                }
                cannotClone.add(object.getClass());
                ret = defaultClone(object);
            }
        }

        if (GlobalSettings.doErrorChecks()) {
            // Check equals and hashCode
            if (!Objects.deepEquals(ret, object) ||
                    !Objects.deepEquals(object, ret)) {
                CheckLogger.notEqualToClone(object);
            }
            if (ret != null && object.hashCode() != ret.hashCode()) {
                CheckLogger.hashCodeNotEqualToClone(object);
            }
        }

        return ret;
    }

    public static byte[] serialize(Serializable object) {
        return defaultSerialize(object);
    }

    public static <T> T deserialize(byte[] object) {
        return defaultDeserialize(object);
    }

    public static long size(Serializable object) {
        return serialize(object).length;
    }
}
