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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import lombok.NonNull;

/**
 * Implements a basic JPanel which can be drawn on, zoomed, and panned using the
 * mouse wheel and clicking and dragging the mouse. Subclasses implement
 * paintZoomedComponent to draw graphics. Additionally, to tell where a mouse
 * click is relative to the graphics drawn, unZoomedPoint should be invoked.
 */
abstract class ZoomableCanvas extends JPanel {
    private static final double SCALE_AMOUNT = 1.1, DEFAULT_MIN_SCALE = 0.15,
            DEFAULT_MAX_SCALE = 10.0;

    private Point dragOrigin = null;
    private AffineTransform transform = new AffineTransform();

    private final double minScale, maxScale;

    public ZoomableCanvas() {
        this(1.0, DEFAULT_MIN_SCALE, DEFAULT_MAX_SCALE);
    }

    public ZoomableCanvas(double initialScaleAmount, double minScale,
                          double maxScale) {
        assert initialScaleAmount != 0.0;
        assert minScale <= maxScale;

        if (initialScaleAmount != 1.0) {
            transform.scale(initialScaleAmount, initialScaleAmount);
        }

        this.minScale = minScale;
        this.maxScale = maxScale;

        addMouseWheelListener(e -> {
            double x = e.getX(), y = e.getY();

            AffineTransform temp = new AffineTransform();
            temp.translate(x, y);

            double scaleFactor =
                    Math.pow(SCALE_AMOUNT, -e.getPreciseWheelRotation());
            temp.scale(scaleFactor, scaleFactor);

            temp.translate(-x, -y);

            double newScaleAmount = transform.getScaleX() * temp.getScaleX();
            if (newScaleAmount > maxScale || newScaleAmount < minScale) {
                return;
            }

            transform.preConcatenate(temp);

            revalidate();
            repaint();
        });

        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // right click
                if (e.getButton() == MouseEvent.BUTTON3) {
                    var menu = new JPopupMenu();
                    var reset = new JMenuItem("Reset zoom");
                    reset.setToolTipText(
                            "Reset the state tree's zoom and pan amount to the default");
                    reset.addActionListener(__ -> {
                        transform = new AffineTransform();
                        revalidate();
                        repaint();
                    });
                    menu.add(reset);
                    menu.show(ZoomableCanvas.this, e.getX(), e.getY());
                    e.consume();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragOrigin = e.getPoint();
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

        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin == null) {
                    return;
                }

                var temp = new AffineTransform();
                temp.translate(-dragOrigin.x + e.getX(),
                        -dragOrigin.y + e.getY());
                transform.preConcatenate(temp);

                dragOrigin = e.getPoint();
                revalidate();
                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent e) {

            }
        });

        setUI();
    }

    private void setUI() {
        setBorder(UIManager.getBorder("TitledBorder.border"));
        setBackground(UIManager.getColor("Tree.background"));
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setUI();
    }

    /**
     * Return the point in the original image corresponding to a point on the
     * component.
     *
     * @param p
     *         the point
     * @return the corresponding point in the original space or null if the
     * canvas has been zoomed out so much that it's degenerated to a point
     * (shouldn't ever happen)
     */
    protected final Point2D unZoomedPoint(@NonNull Point p) {
        try {
            return transform.createInverse().transform(p, null);
        } catch (NoninvertibleTransformException e) {
            // Transform should always be invertible but if it isn't, there is
            // no unique inverse
            return null;
        }
    }

    @Override
    public final void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.transform(transform);
        paintZoomedComponent(g2d);
        g2d.dispose();
    }

    /**
     * Subclasses should override this method to paint the component. The
     * results will be transformed by the class and rendered in the panel.
     *
     * @param g
     *         the Graphics object with a pre-installed transform
     */
    abstract void paintZoomedComponent(Graphics2D g);
}
