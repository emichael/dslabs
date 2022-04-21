/*
 * Copyright (c) 2022 Ellis Michael (emichael@cs.washington.edu)
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

import com.google.common.reflect.ClassPath;
import dslabs.framework.testing.junit.CheckSavedTracesTest;
import dslabs.framework.testing.junit.DSLabsJUnitTest;
import dslabs.framework.testing.visualization.VizConfig;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ClassSearch {
    private static final ClassPath classPath;

    static {
        try {
            classPath = ClassPath.from(ClassLoader.getSystemClassLoader());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?>[] classes(boolean topLevelOnly, boolean notAbstract,
                                      String packagePrefix) {
        List<Class<?>> classes = new ArrayList<>();
        for (var c : topLevelOnly ? classPath.getTopLevelClasses() :
                classPath.getAllClasses()) {
            if (packagePrefix != null &&
                    !c.getPackageName().startsWith(packagePrefix)) {
                continue;
            }
            var clz = c.load();
            if (notAbstract && Modifier.isAbstract(clz.getModifiers())) {
                continue;
            }
            classes.add(c.load());
        }
        return classes.toArray(Class<?>[]::new);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T>[] subclassesOf(Class<T> clz,
                                                         boolean topLevelOnly,
                                                         boolean notAbstract,
                                                         String packagePrefix) {
        List<Class<? extends T>> classes = new ArrayList<>();
        for (var c : classes(topLevelOnly, notAbstract, packagePrefix)) {
            if (clz.isAssignableFrom(c)) {
                classes.add((Class<T>) c);
            }
        }
        return classes.toArray(Class[]::new);
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends DSLabsJUnitTest>[] testClasses() {
        return Arrays.stream(
                             subclassesOf(DSLabsJUnitTest.class, true, true, "dslabs"))
                     .filter(c -> !c.equals(CheckSavedTracesTest.class))
                     .toArray(Class[]::new);
    }

    public static Class<? extends VizConfig>[] vizConfigs() {
        return subclassesOf(VizConfig.class, true, true, "dslabs");
    }
}
