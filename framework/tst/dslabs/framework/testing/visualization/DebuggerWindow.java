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

import com.google.common.collect.Lists;
import dslabs.framework.Address;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.StatePredicate.PredicateResult;
import dslabs.framework.testing.search.SearchSettings;
import dslabs.framework.testing.search.SearchState;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import jiconfont.icons.font_awesome.FontAwesome;
import lombok.NonNull;
import net.miginfocom.layout.AC;
import net.miginfocom.layout.LC;
import net.miginfocom.layout.PlatformDefaults;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jdesktop.swingx.JXMultiSplitPane;
import org.jdesktop.swingx.JXMultiSplitPane.DividerPainter;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jdesktop.swingx.MultiSplitLayout.Divider;
import org.jdesktop.swingx.MultiSplitLayout.Leaf;
import org.jdesktop.swingx.MultiSplitLayout.Split;
import org.jdesktop.swingx.VerticalLayout;


public class DebuggerWindow extends JFrame {
    static {
        /*
         * MigLayout thinks it's so smart and wants to use different spacing on
         * different platforms. We just want consistent behaviour. Therefore, we
         * standardize on Gnome.
         */
        PlatformDefaults.setPlatform(PlatformDefaults.GNOME);

        /*
         * Try to enable GPU acceleration (doesn't seem to work very well) and
         * disable UI scaling.
         *
         * Don't enable GPU acceleration in WSL, though. It does not like it.
         */
        if (!runningInWSL()) {
            System.setProperty("sun.java2d.opengl", "true");
        }
        System.setProperty("sun.java2d.nodraw", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "false");
        System.setProperty("sun.java2d.win.uiScaleX", "1.0");
        System.setProperty("sun.java2d.win.uiScaleX", "1.0");
    }

    /**
     * Try to detect whether the visual debugger is running under the Windows
     * Subsystem for Linux.
     */
    private static boolean runningInWSL() {
        try {
            String procVersion = Files.readString(Path.of("/proc/version"));
            return procVersion.toLowerCase().contains("microsoft");
        } catch (IOException e) {
            return false;
        }
    }

    static final String WINDOW_TITLE = "DSLabs Visual Debugger";
    private static final String PREF_WINDOW_WIDTH = "window_width",
            PREF_WINDOW_HEIGHT = "window_height", PREFS_WINDOW_X =
            "window_location_x", PREFS_WINDOW_Y = "window_location_y",
            PREFS_WINDOW_EXTENDED_STATE = "window_extended_state";
    static final int WINDOW_DEFAULT_WIDTH = 1440, WINDOW_DEFAULT_HEIGHT = 810;
    static final String LINE_WRAPPING_FORMAT = "<html>%1s";

    private final Address[] addresses;

    private final Map<Address, SingleNodePanel> statePanels = new HashMap<>();
    private final Map<Address, JCheckBox> nodesActive = new HashMap<>();

    private final List<Pair<StatePredicate, JLabel>> invariants =
            new ArrayList<>();
    private final List<Pair<StatePredicate, JLabel>> prunes = new ArrayList<>();
    private final List<Pair<StatePredicate, JLabel>> goals = new ArrayList<>();

    private final Pair<JXTaskPane, JLabel> exceptionPanel;

    private final JXMultiSplitPane splitPane;

    private final EventsPanel eventsPanel;
    private final StateTreeCanvas stateTreeCanvas;

    private EventTreeState currentState;
    private final SearchState initialState;
    private final SearchSettings searchSettings;

    private boolean viewDeliveredMessages = false;
    private boolean ignoreSearchSettings = false;

    public DebuggerWindow(final SearchState initialState) {
        this(initialState, null);
    }

    public DebuggerWindow(final SearchState initialState,
                          SearchSettings searchSettings) {
        super(WINDOW_TITLE);

        // Set LAF first so properties are available
        final boolean darkModeEnabled = Utils.setupThemeOnStartup();

        this.initialState = initialState;
        this.searchSettings = searchSettings;
        currentState = EventTreeState.convert(initialState);

        {
            // Clients first, then servers, sorted by name
            List<Address> addresses = new ArrayList<>();
            List<Address> clientAddresses =
                    Lists.newArrayList(currentState.clientAddresses());
            clientAddresses.addAll(
                    Lists.newArrayList(currentState.clientWorkerAddresses()));
            clientAddresses.sort(Comparator.comparing(Object::toString));
            List<Address> serverAddresses =
                    Lists.newArrayList(currentState.serverAddresses());
            serverAddresses.sort(Comparator.comparing(Object::toString));
            addresses.addAll(clientAddresses);
            addresses.addAll(serverAddresses);

            this.addresses = addresses.toArray(Address[]::new);
        }

        setLayout(new MigLayout(new LC().fill(), new AC().fill(),
                new AC().fill()));

        /* ---------------------------------------------------------------------
            SETUP THE MENU BAR
           -------------------------------------------------------------------*/
        final JMenuBar menuBar = new JMenuBar();
        {
            final JMenu fileMenu = new JMenu("Window");
            menuBar.add(fileMenu);
            final JMenuItem revertButton = new JMenuItem("Revert");
            revertButton.setToolTipText(
                    "Revert the visual debugger to its initial configuration from startup");
            revertButton.addActionListener(e -> this.reset());
            fileMenu.add(revertButton);
            final JMenuItem closeButton = new JMenuItem("Quit");
            fileMenu.add(closeButton);
            closeButton.addActionListener(e -> DebuggerWindow.this.dispose());

            final JMenu settingsMenu = new JMenu("Settings");
            menuBar.add(settingsMenu);

            // If the initial state needs delivered messages to be shown, show
            // them by default
            viewDeliveredMessages = currentState.sendsDeliveredMessages();
            JCheckBoxMenuItem viewDeliveredMessagesMenuItem =
                    new JCheckBoxMenuItem("Show delivered messages",
                            viewDeliveredMessages);
            settingsMenu.add(viewDeliveredMessagesMenuItem);
            viewDeliveredMessagesMenuItem.addActionListener(e -> {
                boolean old = viewDeliveredMessages;
                viewDeliveredMessages =
                        viewDeliveredMessagesMenuItem.getState();
                if (old != viewDeliveredMessages) {
                    setState(currentState);
                }
            });

            // TODO: only add menu item if searchSettings actually restricts events
            // (has prunes or prevents message/timer delivery)
            if (searchSettings != null) {
                JCheckBoxMenuItem ignoreSearchSettingsMenuItem =
                        new JCheckBoxMenuItem(
                                "Ignore search event delivery restrictions",
                                false);
                settingsMenu.add(ignoreSearchSettingsMenuItem);
                ignoreSearchSettingsMenuItem.addActionListener(e -> {
                    boolean old = ignoreSearchSettings;
                    ignoreSearchSettings =
                            ignoreSearchSettingsMenuItem.getState();
                    if (old != ignoreSearchSettings) {
                        setState(currentState);
                    }
                });
            }

            settingsMenu.addSeparator();

            final ButtonGroup viewModeButtonGroup = new ButtonGroup();
            final JRadioButtonMenuItem lightMode =
                    new JRadioButtonMenuItem("Light theme", !darkModeEnabled);
            viewModeButtonGroup.add(lightMode);
            settingsMenu.add(lightMode);
            final JRadioButtonMenuItem darkMode =
                    new JRadioButtonMenuItem("Dark theme", darkModeEnabled);
            viewModeButtonGroup.add(darkMode);
            settingsMenu.add(darkMode);

            darkMode.addActionListener(e -> Utils.setupDarkTheme(true));
            lightMode.addActionListener(e -> Utils.setupLightTheme(true));
        }
        setJMenuBar(menuBar);

        /* ---------------------------------------------------------------------
            SETUP THE SIDE BAR
           -------------------------------------------------------------------*/
        final JSplitPane topSplitPane =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        final JSplitPane secondarySplitPane =
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        {
            final JXTaskPaneContainer sideBar = new JXTaskPaneContainer();
            topSplitPane.add(sideBar);

            secondarySplitPane.setResizeWeight(1.0);
            topSplitPane.add(secondarySplitPane);

            JXTaskPane viewHidePane = new JXTaskPane("Show/Hide Nodes");
            for (Address a : addresses) {
                JCheckBox checkbox = new JCheckBox(a.toString());
                checkbox.setSelected(true);
                checkbox.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        layoutNodes();

                        // When we un-hide a node, make sure it gets the right LAF
                        if (checkbox.isSelected()) {
                            SwingUtilities.updateComponentTreeUI(
                                    statePanels.get(a));
                        }

                        splitPane.revalidate();
                        splitPane.repaint();

                        if (checkbox.hasFocus()) {
                            DebuggerWindow.this.transferFocus();
                        }
                    }
                });
                viewHidePane.add(checkbox);
                nodesActive.put(a, checkbox);
            }
            sideBar.add(viewHidePane);

            if (searchSettings != null) {
                addPredicatePaneToSidebar(sideBar, "Invariants",
                        searchSettings.invariants(), this.invariants);
                addPredicatePaneToSidebar(sideBar, "Prunes (Ignored States)",
                        searchSettings.prunes(), this.prunes);
                addPredicatePaneToSidebar(sideBar, "Goals",
                        searchSettings.goals(), this.goals);
                updatePredicatePanes();
            }

            JXTaskPane exceptionPane = new JXTaskPane("Thrown Exception");
            JLabel exceptionLabel = new JLabel();
            exceptionLabel.setIcon(
                    Utils.makeIcon(FontAwesome.EXCLAMATION_TRIANGLE,
                            UIManager.getColor("warningColor")));
            exceptionPanel = new ImmutablePair<>(exceptionPane, exceptionLabel);
            exceptionPane.add(exceptionLabel);
            sideBar.add(exceptionPane);
            updateExceptionPane();

            sideBar.setMinimumSize(new Dimension(20, 0));
            // Don't let sidebar be too large on startup
            sideBar.setPreferredSize(new Dimension(
                    Math.min(sideBar.getPreferredSize().width, 300),
                    sideBar.getPreferredSize().height));
        }
        add(topSplitPane);

        /* ---------------------------------------------------------------------
            ADD THE NODES AND EVENT PANEL
           -------------------------------------------------------------------*/
        for (Address a : addresses) {
            SingleNodePanel panel =
                    new SingleNodePanel(currentState, searchSettings, a, this,
                            viewDeliveredMessages);
            statePanels.put(a, panel);
        }

        splitPane = new JXMultiSplitPane();
        splitPane.setDividerPainter(new DividerPainter() {
            @Override
            protected void doPaint(Graphics2D graphics2D, Divider divider,
                                   int i, int i1) {
                if (divider.isVisible()) {
                    graphics2D.setColor(
                            UIManager.getColor("ToolBar.separatorColor"));
                    graphics2D.drawLine(0, 0, 0, i1);
                }
            }
        });

        layoutNodes();
        secondarySplitPane.add(splitPane);

        eventsPanel = new EventsPanel(this, currentState);
        secondarySplitPane.add(eventsPanel);

        /* ---------------------------------------------------------------------
            SETUP KEYBOARD SHORTCUTS
           -------------------------------------------------------------------*/
        {
            InputMap inputMap =
                    getRootPane().getInputMap(JComponent.WHEN_FOCUSED);
            inputMap.put(KeyStroke.getKeyStroke('p'), "PREVIOUS_EVENT");
            inputMap.put(KeyStroke.getKeyStroke('n'), "NEXT_EVENT");
            inputMap.put(KeyStroke.getKeyStroke('k'), "PREVIOUS_EVENT");
            inputMap.put(KeyStroke.getKeyStroke('j'), "NEXT_EVENT");
            getRootPane().getActionMap()
                         .put("PREVIOUS_EVENT", new AbstractAction() {
                             @Override
                             public void actionPerformed(ActionEvent e) {
                                 if (!currentState.isInitialState()) {
                                     setState(currentState.parent());
                                 }
                             }
                         });
            getRootPane().getActionMap()
                         .put("NEXT_EVENT", new AbstractAction() {
                             @Override
                             public void actionPerformed(ActionEvent e) {
                                 List<EventTreeState> p =
                                         currentState.pathToBestDescendent();
                                 if (p.size() > 0) {
                                     setState(p.get(0));
                                 }
                             }
                         });
        }

        // add(new EventGraphicPanel(), "dock south");
        stateTreeCanvas = new StateTreeCanvas(this);
        stateTreeCanvas.showEvent(currentState);
        add(stateTreeCanvas, "dock south");

        /* ---------------------------------------------------------------------
            SET WINDOW SIZE AND LOCATION
           -------------------------------------------------------------------*/
        addComponentListener(new ComponentListener() {
            @Override
            public void componentResized(ComponentEvent e) {
                final int state = getExtendedState();
                if ((state & MAXIMIZED_HORIZ) == 0) {
                    Utils.PREFERENCES.putInt(PREF_WINDOW_WIDTH, getWidth());
                }
                if ((state & MAXIMIZED_VERT) == 0) {
                    Utils.PREFERENCES.putInt(PREF_WINDOW_HEIGHT, getHeight());
                }
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                final Point p = getLocation();
                Utils.PREFERENCES.putInt(PREFS_WINDOW_X, p.x);
                Utils.PREFERENCES.putInt(PREFS_WINDOW_Y, p.y);
            }

            @Override
            public void componentShown(ComponentEvent e) {
            }

            @Override
            public void componentHidden(ComponentEvent e) {
            }
        });

        addWindowStateListener(new WindowStateListener() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                Utils.PREFERENCES.putInt(PREFS_WINDOW_EXTENDED_STATE,
                        e.getNewState());
            }
        });

        Set<String> preferenceKeys;
        try {
            preferenceKeys = Arrays.stream(Utils.PREFERENCES.keys())
                                   .collect(Collectors.toUnmodifiableSet());
        } catch (BackingStoreException e) {
            preferenceKeys = Collections.emptySet();
        }

        if (preferenceKeys.contains(PREFS_WINDOW_EXTENDED_STATE)) {
            // Only restore maximized state, don't start minimized
            setExtendedState(
                    Utils.PREFERENCES.getInt(PREFS_WINDOW_EXTENDED_STATE,
                            NORMAL) & MAXIMIZED_BOTH);
        }

        pack();

        // TODO: don't exceed size of screen by default?
        setSize(new Dimension(Utils.PREFERENCES.getInt(PREF_WINDOW_WIDTH,
                WINDOW_DEFAULT_WIDTH),
                Utils.PREFERENCES.getInt(PREF_WINDOW_HEIGHT,
                        WINDOW_DEFAULT_HEIGHT)));

        if (preferenceKeys.contains(PREFS_WINDOW_X) &&
                preferenceKeys.contains(PREFS_WINDOW_Y)) {
            setLocation(Utils.PREFERENCES.getInt(PREFS_WINDOW_X, 0),
                    Utils.PREFERENCES.getInt(PREFS_WINDOW_Y, 0));
        } else {
            setLocationRelativeTo(null);
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void addPredicatePaneToSidebar(JXTaskPaneContainer sideBar,
                                           String name,
                                           Collection<StatePredicate> predicates,
                                           List<Pair<StatePredicate, JLabel>> labels) {
        if (predicates.isEmpty()) {
            return;
        }

        JXTaskPane pane = new JXTaskPane(name);
        // Add some space between predicates
        ((VerticalLayout) pane.getContentPane().getLayout()).setGap(12);

        for (StatePredicate predicate : predicates) {
            JLabel label = new JLabel(
                    String.format(LINE_WRAPPING_FORMAT, predicate.name()));
            pane.add(label);
            labels.add(Pair.of(predicate, label));
        }
        sideBar.add(pane);
    }

    private void updatePredicatePanes() {
        for (Pair<StatePredicate, JLabel> e : invariants) {
            StatePredicate invariant = e.getKey();
            JLabel label = e.getValue();

            PredicateResult r = invariant.test(currentState.state());
            if (r.exceptionThrown()) {
                label.setIcon(Utils.makeIcon(FontAwesome.QUESTION_CIRCLE,
                        UIManager.getColor("warningColor")));
                label.setToolTipText(r.errorMessage());
            } else if (r.value()) {
                label.setIcon(Utils.makeIcon(FontAwesome.CHECK_SQUARE));
                label.setToolTipText(null);
            } else {
                label.setIcon(Utils.makeIcon(FontAwesome.EXCLAMATION_TRIANGLE,
                        UIManager.getColor("warningColor")));
                label.setToolTipText(r.detail());
            }
        }

        for (Pair<StatePredicate, JLabel> e : prunes) {
            StatePredicate prune = e.getKey();
            JLabel label = e.getValue();

            PredicateResult r = prune.test(currentState.state());
            if (r.exceptionThrown()) {
                label.setIcon(Utils.makeIcon(FontAwesome.QUESTION_CIRCLE,
                        UIManager.getColor("warningColor")));
                label.setToolTipText(r.errorMessage());
            } else if (r.value()) {
                label.setIcon(Utils.makeIcon(FontAwesome.EYE_SLASH,
                        Utils.desaturate(UIManager.getColor("warningColor"),
                                0.5)));
                label.setToolTipText(r.detail());
            } else {
                // TODO: find better icons, the eye staring at you is creepy
                label.setIcon(Utils.makeIcon(FontAwesome.EYE));
                label.setToolTipText(null);
            }
        }

        for (Pair<StatePredicate, JLabel> e : goals) {
            StatePredicate goal = e.getKey();
            JLabel label = e.getValue();

            PredicateResult r = goal.test(currentState.state());
            if (r.exceptionThrown()) {
                label.setIcon(Utils.makeIcon(FontAwesome.QUESTION_CIRCLE,
                        UIManager.getColor("warningColor")));
                label.setToolTipText(r.errorMessage());
            } else if (r.value()) {
                label.setIcon(Utils.makeIcon(FontAwesome.CHECK_CIRCLE,
                        UIManager.getColor("successColor")));
                label.setToolTipText(null);
            } else {
                // Not using an exclamation because even if the current state is not a goal,
                // this isn't an error. FontAwesome apparently doesn't have a TIMES_SQUARE,
                // so using a circle for both icons.
                label.setIcon(Utils.makeIcon(FontAwesome.TIMES_CIRCLE));
                label.setToolTipText(r.detail());
            }
        }
    }

    private void updateExceptionPane() {
        final JXTaskPane p = exceptionPanel.getLeft();
        final JLabel l = exceptionPanel.getRight();
        final Throwable t = currentState.thrownException();
        if (t == null) {
            p.setVisible(false);
        } else {
            p.setVisible(true);
            p.setCollapsed(false);
            l.setText(String.format(LINE_WRAPPING_FORMAT, t.toString()));
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            l.setToolTipText(sw.toString());
        }
    }

    private void layoutNodes() {
        /*
         * We must reset the JXMultiSplitPane every time. Unfortunately, hiding
         * previously-shown nodes sometimes causes an issue where the minimum
         * size of the far right split gets set to a non-zero value and can't be
         * resized any smaller...
         */

        final MultiSplitLayout layout = splitPane.getMultiSplitLayout();
        layout.setRemoveDividers(true);

        splitPane.removeAll();

        final Split split = new Split();
        split.setRowLayout(true);

        final long numShown =
                nodesActive.values().stream().filter(AbstractButton::isSelected)
                           .count();

        if (numShown == 0) {
            return;
        }

        final List<MultiSplitLayout.Node> layoutNodes = new ArrayList<>();
        final double weight = 1.0 / numShown;

        for (Address a : addresses) {
            if (!nodesActive.get(a).isSelected()) {
                continue;
            }

            // TODO: make sure they're unique?
            Leaf l = new Leaf(a.toString());
            layoutNodes.add(l);
            l.setWeight(weight);
            layoutNodes.add(new Divider());
        }
        // Don't end with a divider
        layoutNodes.remove(layoutNodes.size() - 1);

        // XXX: Disgusting hack to show only 1 leaf node
        if (numShown == 1) {
            Leaf l = new Leaf("DUMMY-LEAF-1");
            l.setWeight(0.0);
            layoutNodes.add(new Divider());
            layoutNodes.add(l);
        }

        split.setChildren(layoutNodes);

        splitPane.setModel(split);

        for (Address a : addresses) {
            if (!nodesActive.get(a).isSelected()) {
                continue;
            }
            splitPane.add(statePanels.get(a), a.toString());
        }

        if (numShown == 1) {
            splitPane.add(new JPanel(), "DUMMY-LEAF-1");
            layout.displayNode("DUMMY-LEAF-1", false);
        }

        layout.setLayoutMode(MultiSplitLayout.NO_MIN_SIZE_LAYOUT);
        layout.setFloatingDividers(true);
        layout.layoutByWeight(splitPane);
    }

    void reset() {
        stateTreeCanvas.reset();
        eventsPanel.reset();
        setState(EventTreeState.convert(DebuggerWindow.this.initialState));
    }

    void deliverEvent(Event e) {
        EventTreeState newState = currentState.step(e);
        assert newState != null;
        setState(newState);
    }

    void setState(@NonNull EventTreeState s) {
        stateTreeCanvas.showEvent(s);
        currentState = s;
        for (Address a : currentState.addresses()) {
            assert statePanels.containsKey(a);
            statePanels.get(a).updateState(currentState,
                    ignoreSearchSettings ? null : searchSettings,
                    viewDeliveredMessages);
        }
        eventsPanel.update(currentState);
        updatePredicatePanes();
        updateExceptionPane();
    }
}
