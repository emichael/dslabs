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

package dslabs.framework.testing.junit;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dslabs.framework.testing.utils.ClassSearch;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// NB: I checked, and ClassSearch can definitely find class files in jars

public class JUnitSanityCheckTest {
    @SuppressWarnings("unchecked")
    private static Class<? extends DSLabsJUnitTest>[] nonIgnoredTestClasses() {
        return Arrays.stream(ClassSearch.testClasses()).filter(c ->
                             c.getAnnotation(IgnoredDSlabsJUnitTest.class) == null)
                     .toArray(Class[]::new);
    }

    @Test
    public void testSuitesRunWithDSLabsTestRunner() {
        for (var c : nonIgnoredTestClasses()) {
            var runWith = c.getAnnotation(RunWith.class);
            assertNotNull(runWith);
            assertEquals(runWith.value(), DSLabsTestRunner.class);
        }
    }

    @Test
    public void testSuitesPublicNonAbstract() {
        for (var c : nonIgnoredTestClasses()) {
            assertTrue(Modifier.isPublic(c.getModifiers()));
            assertFalse(Modifier.isAbstract(c.getModifiers()));
        }
    }

    @Test
    public void testSuitesNotMarkedWithMethodOrder() {
        for (var c : nonIgnoredTestClasses()) {
            assertNull(c + " not annotated with @FixMethodOrder",
                    c.getAnnotation(FixMethodOrder.class));
        }
    }

    @Test
    public void testSuitesAnnotatedWithLab() {
        for (var c : nonIgnoredTestClasses()) {
            if (Modifier.isAbstract(c.getModifiers())) {
                continue;
            }
            var lab = c.getAnnotation(Lab.class);
            assertNotNull(c.getName() + " is annotated with @Lab", lab);
            assertNotNull(lab.value());
        }
    }

    @Test
    public void testPartsAssignedCorrectly() {
        Multimap<String, Class<? extends DSLabsJUnitTest>> clzs =
                HashMultimap.create();
        for (var c : nonIgnoredTestClasses()) {
            clzs.put(c.getAnnotation(Lab.class).value(), c);
        }

        for (var l : clzs.keySet()) {
            var testClasses = new ArrayList<>(clzs.get(l));
            assertFalse(testClasses.isEmpty());
            if (testClasses.size() == 1) {
                // Check that labs with one part only aren't labeled with a part
                assertNull(testClasses.get(0).getAnnotation(Part.class));
            } else {
                // Check that parts are labeled sequentially
                for (var c : testClasses) {
                    assertNotNull(c.getAnnotation(Part.class));
                }
                testClasses.sort(Comparator.comparingInt(
                        c -> c.getAnnotation(Part.class).value()));
                int i = 1;
                for (var c : testClasses) {
                    assertEquals(i, c.getAnnotation(Part.class).value());
                    i++;
                }
            }
        }
    }

    @Test
    public void testMethods() {
        for (var c : nonIgnoredTestClasses()) {
            Part p;
            var part = (p = c.getAnnotation(Part.class)) == null ? "" :
                    p.value() + ".";
            List<String> testNumbers = new ArrayList<>();
            for (var m : c.getDeclaredMethods()) {
                if (m.getAnnotation(Test.class) != null) {
                    // All tests must have a description
                    assertNotNull(m.getAnnotation(TestDescription.class));

                    // Test classes are public void
                    assertTrue(Modifier.isPublic(m.getModifiers()));
                    assertEquals(Void.TYPE, m.getReturnType());

                    // Now, we check that each test is named properly
                    assertTrue(m.getName()
                                .matches("^test\\d+[A-Za-z][A-Za-z_0-9]*$"));

                    testNumbers.add(m.getName().replaceFirst(
                            "^test(\\d+)[A-Za-z][A-Za-z_0-9]*$", "$1"));
                }
            }

            // Test numbering must be 1 .. N or <PART>.1 .. <PART>.N
            Set<String> testNumberSet = new HashSet<>(testNumbers);
            int numDigits =
                    Math.max(2, Integer.toString(testNumbers.size()).length());
            for (int i = 1; i <= testNumbers.size(); i++) {
                String s = Integer.toString(i);
                s = "0".repeat(numDigits - s.length()) + s;
                assertTrue(testNumberSet.contains(s));
            }
        }
    }
}
