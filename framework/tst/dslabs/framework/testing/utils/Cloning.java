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

import com.google.common.base.Objects;
import com.rits.cloning.Cloner;
import com.rits.cloning.ICloningStrategy;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.SerializationUtils;

/**
 * Wrapper around serialization utils to allow easy replacement in the future.
 */
public abstract class Cloning {
    private static final Cloner jdclCloner = new Cloner();
    private static final Set<Class<?>> cannotClone =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    static {
        jdclCloner.registerCloningStrategy(new ICloningStrategy() {
            /*
                This cloning library works for most things. However, since it's
                not using default serializers, it's only safe to ignore
                transient fields in this package.

                TODO: don't use transient fields at all??
             */
            @Override
            public Strategy strategyFor(Object toBeCloned, Field field) {
                final int modifiers = field.getModifiers();
                if (Modifier.isTransient(modifiers) &&
                        toBeCloned.getClass().getPackage().getName()
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
                cannotClone.add(object.getClass());
                ret = defaultClone(object);
            }
        }

        if (GlobalSettings.doChecks()) {
            // Check equals and hashCode
            if (!Objects.equal(ret, object)) {
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
