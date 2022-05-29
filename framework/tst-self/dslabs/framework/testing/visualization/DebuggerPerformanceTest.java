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

import dslabs.framework.testing.junit.Lab;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.utils.ClassSearch;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import javax.swing.JButton;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This test class is used to test the performance of the visual debugger. The
 * numbers reported by these tests are to be taken with a grain of salt and
 * should not be compared across machines or even monitor configurations. They
 * are dependent on monitor size, graphics stack, background processes etc.
 *
 * This class is ignored in the default test suite. It will not be run when
 * {@code make test} is invoked, for example. Instead, it should be run manually
 * to test the effects of various changes to the visual debugger.
 */
@Ignore
public class DebuggerPerformanceTest {

    private Point messageButtonLocation(DebuggerWindow window) {
        Queue<Component> traversalQueue = new LinkedList<>();
        traversalQueue.add(window);
        synchronized (window.getTreeLock()) {
            while (!traversalQueue.isEmpty()) {
                Component c = traversalQueue.poll();
                assert c != null;
                if (c instanceof JButton &&
                        ((JButton) c).getToolTipText() != null &&
                        ((JButton) c).getToolTipText().toLowerCase()
                                     .contains("deliver message")) {
                    return c.getLocationOnScreen();
                }
                if (c instanceof Container) {
                    traversalQueue.addAll(
                            Arrays.asList(((Container) c).getComponents()));
                }
            }
        }
        return null;
    }

    private double singleTestTimeSecs()
            throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException, AWTException {

        VizConfig config = null;
        for (var c : ClassSearch.vizConfigs()) {
            Lab l;
            if ((l = c.getAnnotation(Lab.class)) != null &&
                    l.value().equals("0")) {
                config = c.getDeclaredConstructor().newInstance();
                break;
            }
        }

        if (config == null) {
            throw new RuntimeException("Could not find viz config for lab 0");
        }

        SearchState s =
                config.getInitialState(new String[]{"1", "5", "Hello,Goodbye"});

        final long startTime = System.nanoTime();

        Robot robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(10);

        DebuggerWindow window = new DebuggerWindow(s);

        robot.waitForIdle();

        window.getGraphicsConfiguration().getDevice()
              .setFullScreenWindow(window);

        robot.waitForIdle();

        for (int i = 0; i < 10; i++) {
            Point p = messageButtonLocation(window);
            assert p != null;
            robot.mouseMove(p.x, p.y);
            robot.setAutoDelay(0);
            robot.setAutoWaitForIdle(false);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.setAutoDelay(10);
            robot.setAutoWaitForIdle(true);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        }

        final long endTime = System.nanoTime();
        final double seconds = (endTime - startTime) / 1000000000f;
        System.out.println("Total time seconds: " + seconds);

        window.dispose();

        return seconds;
    }

    @Test
    public void testDebuggerPerformance()
            throws NoSuchMethodException, InvocationTargetException,
            InstantiationException, IllegalAccessException, AWTException {
        singleTestTimeSecs();
    }

    @Test
    public void multiRunPerformanceTest()
            throws InvocationTargetException, NoSuchMethodException,
            InstantiationException, IllegalAccessException, AWTException {
        final int NUM_RUNS = 10;
        double totalSecs = 0;
        for (int i = 0; i < NUM_RUNS; i++) {
            totalSecs += singleTestTimeSecs();
        }
        System.out.println("Average time: " + totalSecs / NUM_RUNS);
    }
}
