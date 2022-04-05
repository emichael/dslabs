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

package dslabs.framework.testing.newviz;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

/**
 * Implements a basic JPanel which can be drawn on, zoomed, and panned using the
 * mouse wheel and clicking and dragging the mouse. Sub-classes should apply the
 * transform from getTransform() before drawing 2D graphics. Additionally, to
 * tell where a mouse click is relative to the graphics drawn, the inverse of
 * the transform should first be applied to the point.
 */
class ZoomableCanvas extends JPanel
        implements MouseWheelListener, MouseMotionListener, MouseListener {
    private static final double SCALE_STEP = 0.1d;

    private double scale = 1.0;
    private double deltaX, deltaY;
    private Point dragOrigin = null;

    public ZoomableCanvas() {
        this(-1.0);
    }

    public ZoomableCanvas(double scale) {
        if (scale > 0) {
            this.scale = scale;
        }

        addMouseWheelListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

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

    public AffineTransform getTransform() {
        AffineTransform at = new AffineTransform();
        at.translate(deltaX, deltaY);
        at.scale(scale, scale);
        return at;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double zoomFactor = -SCALE_STEP * e.getPreciseWheelRotation() * scale;
        scale += zoomFactor;
        scale = Math.max(scale, SCALE_STEP);
        revalidate();
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragOrigin == null) {
            return;
        }
        deltaX -= dragOrigin.x - e.getX();
        deltaY -= dragOrigin.y - e.getY();
        dragOrigin = e.getPoint();
        revalidate();
        repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e) {

    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // right click
        if (e.getButton() == MouseEvent.BUTTON3) {
            var menu = new JPopupMenu();
            var reset = new JMenuItem("Reset zoom");
            reset.setToolTipText(
                    "Reset the state tree's zoom and pan amount to the default");
            reset.addActionListener(__ -> {
                scale = 1.0;
                deltaX = 0;
                deltaY = 0;
                revalidate();
                repaint();
            });
            menu.add(reset);
            menu.show(this, e.getX(), e.getY());
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
}

