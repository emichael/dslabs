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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

class StateTreeCanvas extends ZoomableCanvas {
    Node root = null;
    // TODO: set of nodes not necessary, can just iterate the tree
    Set<Node> nodes = new HashSet<>();
    Node selected = null;

    private static final int DEFAULT_X_SIZE = 1080, DEFAULT_Y_SIZE = 200,
            MIN_Y_SIZE = 150;

    private static final int CIRCLE_RADIUS = 40, CIRCLE_GAP = 60;
    private static final int X_OFFSET = 100, Y_OFFSET = 75;

    StateTreeCanvas(DebuggerWindow parent) {
        // XXX: is allowing flexibility okay? Do we want this to sometimes resize?
        setMinimumSize(new Dimension(getMinimumSize().width, MIN_Y_SIZE));
        setPreferredSize(new Dimension(DEFAULT_X_SIZE, DEFAULT_Y_SIZE));

        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isConsumed() || e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                var p = unZoomedPoint(e.getPoint());

                // TODO: binary search instead of linear scan
                for (var n : nodes) {
                    if (n.contains(p)) {
                        // Don't do anything if the state is already selected
                        if (n != selected) {
                            parent.setState(n.state);
                        }
                        e.consume();
                        break;
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
    }

    /*
     * This replicates much of the logic from EventTreeState, but it's better to
     * keep these things separate and less coupled.
     */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class Node implements Shape {
        private final EventTreeState state;
        private final Node parent;

        private final List<Node> children = new ArrayList<>();

        static Node createRoot(EventTreeState state) {
            return new Node(state, null);
        }

        Node addChild(EventTreeState s) {
            Node n = new Node(s, this);
            children.add(n);
            return n;
        }

        int depth() {
            if (parent == null) {
                return 0;
            }
            return parent.depth() + 1;
        }

        int width() {
            if (children.isEmpty()) {
                return 1;
            }
            return children.stream()
                           .reduce(0, (i, s) -> i + s.width(), Integer::sum);
        }

        int x() {
            return depth();
        }

        int y() {
            if (parent == null) {
                return 0;
            }
            int y = parent.y();
            for (Node n : parent.children) {
                if (n == this) {
                    break;
                }
                y += n.width();
            }
            return y;
        }

        @Delegate
        private Shape circle() {
            return new Ellipse2D.Double(x() * CIRCLE_GAP + X_OFFSET,
                    -y() * CIRCLE_GAP + Y_OFFSET, CIRCLE_RADIUS, CIRCLE_RADIUS);
        }
    }

    void showEvent(EventTreeState state) {
        // TODO: resize to show entire tree?

        if (root == null) {
            root = Node.createRoot(state.pathFromInitial().get(0));
            nodes.add(root);
        }

        Node parent = root;
        outer:
        for (EventTreeState s : state.pathFromInitial()) {
            if (s.isInitialState()) {
                assert s.equals(root.state);
                continue;
            }
            for (Node n : parent.children) {
                if (s.equals(n.state)) {
                    parent = n;
                    continue outer;
                }
            }
            parent = parent.addChild(s);
            nodes.add(parent);
        }

        selected = parent;

        revalidate();
        repaint();
    }

    @Override
    public void paintZoomedComponent(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.setStroke(
                new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                        0, new float[]{5}, 0));
        paintNode(g, root);
    }

    private void paintNode(Graphics2D g, Node n) {
        for (Node child : n.children) {
            g.drawLine((int) n.getBounds2D().getCenterX(),
                    (int) n.getBounds2D().getCenterY(),
                    (int) child.getBounds2D().getCenterX(),
                    (int) child.getBounds2D().getCenterY());
        }

        g.fill(n);
        if (n == selected) {
            var gTemp = (Graphics2D) g.create();
            gTemp.setColor(Color.RED);
            gTemp.setStroke(new BasicStroke(2.0f));
            gTemp.draw(n);
            gTemp.dispose();
        }

        for (Node child : n.children) {
            paintNode(g, child);
        }
    }

    void reset() {
        root = null;
        selected = null;
        nodes.clear();
    }
}
