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
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
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

import dslabs.framework.testing.Event;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import jiconfont.icons.font_awesome.FontAwesome;
import net.miginfocom.layout.AC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

class EventsPanel extends JPanel {
    private BaseJTree initialState;
    private final List<Pair<EventTreeState, ObjectJTree>> events =
            new ArrayList<>();
    private final JPanel inner;
    private final DebuggerWindow parent;

    EventsPanel(DebuggerWindow parent, EventTreeState initialState) {
        this.parent = parent;

        setLayout(new MigLayout(new LC().wrapAfter(1).fill(), null,
                new AC().grow(100, 1)));
        add(new JLabel("<html><h3>Events</h3></html>"), "align center");

        inner = new JPanel(
                new MigLayout(new LC().wrapAfter(1), new AC().gap("0"),
                        new AC().gap("0")));

        JScrollPane scrollPane = Utils.scrollPane(inner);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        add(scrollPane, "grow");

        update(initialState);

        // TODO: should this go in DebuggerWindow?
        setPreferredSize(new Dimension(300, getPreferredSize().height));
        setMinimumSize(new Dimension(5, 0));
    }

    private ObjectJTree addEventTreeState(final EventTreeState s) {
        final JPanel box =
                new JPanel(new MigLayout(null, null, new AC().align("top")));

        final JButton button =
                new JButton(Utils.makeIcon(FontAwesome.ARROW_RIGHT));
        button.setFocusable(false);
        box.add(button, "pad 0 0");
        button.addActionListener(e -> parent.setState(s));

        if (s.isInitialState()) {
            // XXX: kind of ugly way to do this
            initialState =
                    new BaseJTree(new DefaultMutableTreeNode("Initial State"));
            initialState.rootIcon(Utils.makeIcon(FontAwesome.FLAG));
            box.add(initialState);
            inner.add(box);
            button.setToolTipText(
                    "Return to the initial state of the system, before any events were delivered");
            return null;
        } else if (s.previousEvent().isMessage()) {
            button.setToolTipText(
                    "Go to the state after this message was delivered");
        } else {
            button.setToolTipText(
                    "Go to the state after this timer was delivered");
        }

        Event e = s.previousEvent();
        ObjectJTree tree =
                new ObjectJTree(e.isMessage() ? e.message() : e.timer());

        tree.collapseRow(0);
        box.add(tree);

        inner.add(box);

        events.add(new ImmutablePair<>(s, tree));

        return tree;
    }

    void update(EventTreeState s) {
        Iterator<EventTreeState> i1 = s.pathFromInitial().iterator();
        Iterator<Pair<EventTreeState, ObjectJTree>> i2 = events.iterator();

        // Ignore initial state, handle separately using label
        assert i1.hasNext();
        // TODO: handle this better...
        if (initialState == null) {
            addEventTreeState(i1.next());
        } else {
            i1.next();
        }

        // TODO: slightly hacky
        if (i1.hasNext()) {
            initialState.setTreeDisplayType(JTreeDisplayType.DEFAULT);
        } else {
            initialState.setTreeDisplayType(JTreeDisplayType.HIGHLIGHT);
        }

        while (i1.hasNext() && i2.hasNext()) {
            EventTreeState e = i1.next();
            Pair<EventTreeState, ObjectJTree> p = i2.next();

            // TODO: use .equals??
            if (e != p.getLeft()) {
                // XXX: brittle, depends on container structure
                inner.remove(p.getRight().getParent());
                i2.remove();
                while (i2.hasNext()) {
                    inner.remove(i2.next().getRight().getParent());
                    i2.remove();
                }
                ObjectJTree tree = addEventTreeState(e);
                // TODO: don't actually update if unnecessary
                if (!i1.hasNext()) {
                    tree.setTreeDisplayType(JTreeDisplayType.HIGHLIGHT);
                }
                i2 = null;
                break;
            }

            // TODO: don't actually update if unnecessary
            if (i1.hasNext()) {
                p.getRight().setTreeDisplayType(JTreeDisplayType.DEFAULT);
            } else {
                p.getRight().setTreeDisplayType(JTreeDisplayType.HIGHLIGHT);
            }
        }

        while (i1.hasNext()) {
            ObjectJTree tree = addEventTreeState(i1.next());
            // TODO: don't actually update if unnecessary
            if (!i1.hasNext()) {
                tree.setTreeDisplayType(JTreeDisplayType.HIGHLIGHT);
            }
            i2 = null;
        }

        i1 = s.pathToBestDescendent().iterator();

        // If i2's list was altered, it was previously empty
        while (i1.hasNext() && i2 != null && i2.hasNext()) {
            EventTreeState e = i1.next();
            Pair<EventTreeState, ObjectJTree> p = i2.next();

            // XXX: repeated code...
            // TODO: use .equals??
            if (e != p.getLeft()) {
                // XXX: brittle, depends on container structure
                inner.remove(p.getRight().getParent());
                i2.remove();
                while (i2.hasNext()) {
                    inner.remove(i2.next().getRight().getParent());
                    i2.remove();
                }
                ObjectJTree tree = addEventTreeState(e);
                // TODO: don't actually update if unnecessary
                tree.setTreeDisplayType(JTreeDisplayType.LOWLIGHT);
                i2 = null;
                break;
            } else {
                p.getRight().setTreeDisplayType(JTreeDisplayType.LOWLIGHT);
            }
        }

        while (i1.hasNext()) {
            i2 = null;
            ObjectJTree tree = addEventTreeState(i1.next());
            tree.setTreeDisplayType(JTreeDisplayType.LOWLIGHT);
        }

        // TODO: this giant method barely works and depends on how the best path is chosen...
        // should probably switch to indices or make shallow copies before removal or something?

        while (i2 != null && i2.hasNext()) {
            inner.remove(i2.next().getRight().getParent());
            i2.remove();
        }

        // Need both revalidate and repaint here for whatever reason
        inner.revalidate();
        inner.repaint();
    }

    void reset() {
        inner.removeAll();
        events.clear();
        initialState = null;
    }
}
