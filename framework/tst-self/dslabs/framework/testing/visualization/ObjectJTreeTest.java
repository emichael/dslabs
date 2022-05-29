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

package dslabs.framework.testing.visualization;


import dslabs.framework.Address;
import dslabs.framework.Node;
import dslabs.framework.testing.LocalAddress;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.JLabel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;


// TODO: expand these tests to be much more extensive
public class ObjectJTreeTest {

    @SneakyThrows
    private static String diffStatus(ObjectJTree t, int row) {
        Object node = t.getPathForRow(row).getLastPathComponent();
        for (Class<?> c = node.getClass(); !c.equals(Object.class);
             c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("diffStatus");
                m.setAccessible(true);
                return m.invoke(node).toString();
            } catch (NoSuchMethodException e) {
                continue;
            }
        }
        return null;
    }

    private static String rowText(ObjectJTree t, int row) {
        Object treeNode = t.getPathForRow(row).getLastPathComponent();
        return ((JLabel) t.getCellRenderer()
                          .getTreeCellRendererComponent(t, treeNode,
                                  t.isRowSelected(row), t.isExpanded(row),
                                  t.getModel().isLeaf(treeNode), row,
                                  false)).getText();
    }

    private static Integer findRowNumber(ObjectJTree t, String regex) {
        for (int i = 0; i < t.getRowCount(); i++) {
            if (Pattern.matches(regex, rowText(t, i))) {
                return i;
            }
        }
        return null;
    }

    private static boolean rowExpands(ObjectJTree t, int row) {
        return !t.getModel()
                 .isLeaf(t.getPathForRow(row).getLastPathComponent());
    }

    @Test(timeout = 3000)
    public void basicTest() {
        ObjectJTree t = new ObjectJTree(new Foo(new LocalAddress("foo")));
        t.clearDiffObject();
        t.update(new Foo(new LocalAddress("bar")));
        t.update(new Foo(new LocalAddress("baz")),
                new Foo(new LocalAddress("foo")));
        t.expandRow(0);
    }

    @Test(timeout = 3000)
    public void stateTreeTest() {
        ObjectJTree t = new ObjectJTree(new Foo(new LocalAddress("foo")));
        t.collapseRow(0);
        assertNull(findRowNumber(t, ".*v03.*"));
        assertNull(findRowNumber(t, ".*v07.*"));
        t.expandRow(0);
        assertFalse(rowExpands(t, findRowNumber(t, ".*v03.*")));
        int v7Row = findRowNumber(t, ".*v07.*");
        assertTrue(rowExpands(t, v7Row));
        assertNull(findRowNumber(t, ".*qwerty.*"));
        assertNull(findRowNumber(t, ".*9876.*"));
        t.expandRow(v7Row);
        assertTrue(Pattern.matches(".*qwerty.*", rowText(t, v7Row + 1)));
        assertTrue(Pattern.matches(".*9876.*", rowText(t, v7Row + 2)));
    }

    private void expandAll(ObjectJTree t) {
        int numRows;
        do {
            numRows = t.getRowCount();
            for (int i = 0; i < t.getRowCount(); i++) {
                t.expandRow(i);
            }
        } while (numRows != t.getRowCount());
    }

    @Test(timeout = 3000)
    public void diffTest() {
        ObjectJTree t = new ObjectJTree(new Foo(new LocalAddress("foo")));
        Foo f2 = new Foo(new LocalAddress("foo"));
        f2.v01 = 2;
        f2.v02 = 9;
        f2.v03 = null;
        f2.v04 = new int[]{1, 2, 3, 4};
        f2.v05 = null;

        t.update(f2);

        assertEquals("CHANGED", diffStatus(t, findRowNumber(t, ".*v01.*")));
        assertEquals("UNCHANGED", diffStatus(t, findRowNumber(t, ".*v07.*")));

        t.clearDiffObject();

        expandAll(t);

        for (int i = 0; i < t.getRowCount(); i++) {
            assertEquals("NOT_DIFFED", diffStatus(t, i));
        }

        t.update(f2, null);
        assertEquals("CHANGED", diffStatus(t, 0));
        for (int i = 1; i < t.getRowCount(); i++) {
            assertEquals("NEW", diffStatus(t, i));
        }
    }

}

@EqualsAndHashCode(callSuper = true)
class Foo extends Node {
    int v01 = 0;
    Integer v02 = null;
    Integer v03 = 5;
    int[] v04 = null;
    int[] v05 = new int[3];
    Address v06 = new LocalAddress("asdf");
    List<Object> v07 = new LinkedList<>() {
        @Override
        public String toString() {
            return "[]";
        }
    };

    Integer[] v08 = new Integer[]{0, 5, 3};
    Integer[] v09 = null;

    Object[] v10 = new Object[]{"foo", 4, new int[]{1, 2, 3}};

    Map<String, Integer> v11 = new HashMap<>();

    {
        v07.add("qwerty");
        v07.add(9876);

        v11.put("lbskj", 78525);
        v11.put("asiudb", 98623578);
    }

    protected Foo(@NonNull Address address) {
        super(address);
    }

    @Override
    public void init() {
    }
}
