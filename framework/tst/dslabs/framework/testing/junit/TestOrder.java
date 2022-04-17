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

import org.junit.runner.manipulation.Sorter;

class TestOrder extends Sorter {
    public TestOrder() {
        // Sort methods by lab, test name
        super((o1, o2) -> {
            if (o1.isSuite()) {
                assert o2.isSuite();
                var l1 = o1.getAnnotation(Lab.class);
                var l2 = o2.getAnnotation(Lab.class);
                assert l1 != null;
                assert l2 != null;

                if (!l1.value().equals(l2.value())) {
                    // This shouldn't happen right now...
                    return l1.value().compareTo(l2.value());
                }

                var p1 = o1.getAnnotation(Part.class);
                var p2 = o2.getAnnotation(Part.class);

                // If running multiple test suites, they should be annotated with part numbers
                assert p1 != null;
                assert p2 != null;

                return Integer.compare(p1.value(), p2.value());
            }

            assert o1.isTest();
            assert o2.isTest();

            return o1.getMethodName().compareTo(o2.getMethodName());
        });
    }
}
