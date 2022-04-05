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

import com.google.common.collect.Lists;
import dslabs.framework.Address;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.StatePredicate;
import dslabs.framework.testing.search.SearchState;
import dslabs.framework.testing.search.SearchSettings;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.commons.lang3.tuple.Pair;
import org.jdesktop.swingx.JXMultiSplitPane;
import org.jdesktop.swingx.JXMultiSplitPane.DividerPainter;
import org.jdesktop.swingx.JXTaskPane;
import org.jdesktop.swingx.JXTaskPaneContainer;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jdesktop.swingx.MultiSplitLayout.Divider;
import org.jdesktop.swingx.MultiSplitLayout.Leaf;
import org.jdesktop.swingx.MultiSplitLayout.Split;


public class DebuggerWindow extends JFrame {
    static {
        /*
         * MigLayout thinks it's so smart and wants to use different spacing on
         * different platforms. We just want consistent behaviour. Therefore, we
         * standardize on Gnome.
         */
        PlatformDefaults.setPlatform(PlatformDefaults.GNOME);

        // Try to enable GPU acceleration (doesn't seem to work) and disable UI scaling
        System.setProperty("sun.java2d.opengl", "true");
        System.setProperty("sun.java2d.nodraw", "true");
        System.setProperty("sun.java2d.uiScale.enabled", "false");
        System.setProperty("sun.java2d.win.uiScaleX", "1.0");
        System.setProperty("sun.java2d.win.uiScaleX", "1.0");
    }


    static final String WINDOW_TITLE = "DSLabs Visual Debugger";
    static final int WINDOW_DEFAULT_WIDTH = 1440, WINDOW_DEFAULT_HEIGHT = 810;

    private final Address[] addresses;

    private final Map<Address, SingleNodePanel> statePanels = new HashMap<>();
    private final Map<Address, JCheckBox> nodesActive = new HashMap<>();

    private final List<Pair<StatePredicate, JLabel>> invariants = new ArrayList<>();
    private final List<Pair<StatePredicate, JLabel>> prunes = new ArrayList<>();
    private final List<Pair<StatePredicate, JLabel>> goals = new ArrayList<>();

    private final JXMultiSplitPane splitPane;

    private final EventsPanel eventsPanel;
    private final StateTreeCanvas stateTreeCanvas;

    private EventTreeState currentState;
    private final SearchState initialState;
    private final SearchSettings searchSettings;

    private boolean viewDeliveredMessages = false;

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

            final JMenu viewMenu = new JMenu("View");
            menuBar.add(viewMenu);

            JCheckBoxMenuItem viewDeliveredMessagesMenuItem =
                    new JCheckBoxMenuItem("View delivered messages", false);
            viewMenu.add(viewDeliveredMessagesMenuItem);
            viewDeliveredMessagesMenuItem.addActionListener(e -> {
                boolean old = viewDeliveredMessages;
                viewDeliveredMessages =
                        viewDeliveredMessagesMenuItem.getState();
                if (old != viewDeliveredMessages) {
                    setState(currentState);
                }
            });

            viewMenu.addSeparator();

            final ButtonGroup viewModeButtonGroup = new ButtonGroup();
            final JRadioButtonMenuItem lightMode =
                    new JRadioButtonMenuItem("Light theme", !darkModeEnabled);
            viewModeButtonGroup.add(lightMode);
            viewMenu.add(lightMode);
            final JRadioButtonMenuItem darkMode =
                    new JRadioButtonMenuItem("Dark theme", darkModeEnabled);
            viewModeButtonGroup.add(darkMode);
            viewMenu.add(darkMode);

            darkMode.addActionListener(e -> Utils.setupDarkTheme(true));
            lightMode.addActionListener(e -> Utils.setupLightTheme(true));
        }
        add(menuBar, "dock north");

        /* ---------------------------------------------------------------------
            Setup the side bar
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
                addPredicatePaneToSidebar(sideBar, "Invariants", searchSettings.invariants(),
                                          this.invariants);
                addPredicatePaneToSidebar(sideBar, "Prunes", searchSettings.prunes(),
                                          this.prunes);
                addPredicatePaneToSidebar(sideBar, "Goals", searchSettings.goals(),
                                          this.goals);
                updatePredicatePanes();
            }
            sideBar.setMinimumSize(new Dimension(20, 0));
        }
        add(topSplitPane);

        /* ---------------------------------------------------------------------
            ADD THE NODES AND EVENT PANEL
           -------------------------------------------------------------------*/
        for (Address a : addresses) {
            SingleNodePanel panel = new SingleNodePanel(currentState, a, this);
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

        pack();
        // TODO: don't exceed size of screen
        setSize(new Dimension(WINDOW_DEFAULT_WIDTH, WINDOW_DEFAULT_HEIGHT));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    private void addPredicatePaneToSidebar(JXTaskPaneContainer sideBar, String name,
                                           Collection<StatePredicate> predicates,
                                           List<Pair<StatePredicate, JLabel>> labels) {
        if (predicates.isEmpty()) {
            return;
        }

        JXTaskPane pane = new JXTaskPane(name);
        for (StatePredicate predicate : predicates) {
            JLabel label = new JLabel(predicate.name());
            pane.add(label);
            labels.add(Pair.of(predicate, label));
        }
        sideBar.add(pane);
    }

    private void updatePredicatePanes() {
        for (Pair<StatePredicate, JLabel> e : invariants) {
            StatePredicate invariant = e.getKey();
            JLabel label = e.getValue();

            if (invariant.test(currentState.state())) {
                label.setIcon(Utils.makeIcon(FontAwesome.CHECK_SQUARE));
            } else {
                label.setIcon(Utils.makeIcon(FontAwesome.EXCLAMATION_TRIANGLE,
                        UIManager.getColor("warningColor")));
                label.setToolTipText(invariant.detail(currentState.state()));
            }
        }

        for (Pair<StatePredicate, JLabel> e : prunes) {
            StatePredicate prune = e.getKey();
            JLabel label = e.getValue();

            if (prune.test(currentState.state())) {
                // Not sure what icon to put here: scissors? then what icon to use for the
                // un-pruned?
                label.setIcon(Utils.makeIcon(FontAwesome.LOCK,
                        UIManager.getColor("warningColor")));
                label.setToolTipText(prune.detail(currentState.state()));
            } else {
                label.setIcon(Utils.makeIcon(FontAwesome.UNLOCK));
            }
        }

        for (Pair<StatePredicate, JLabel> e : goals) {
            StatePredicate goal = e.getKey();
            JLabel label = e.getValue();

            if (goal.test(currentState.state())) {
                label.setIcon(Utils.makeIcon(FontAwesome.CHECK_CIRCLE,
                        UIManager.getColor("successColor")));
            } else {
                // Not using an exclamation because even if the current state is not a goal,
                // this isn't an error. FontAwesome apparently doesn't have a TIMES_SQUARE,
                // so using a circle for both icons.
                label.setIcon(Utils.makeIcon(FontAwesome.TIMES_CIRCLE));
                label.setToolTipText(goal.detail(currentState.state()));
            }
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
            statePanels.get(a).updateState(currentState, viewDeliveredMessages);
        }
        eventsPanel.update(currentState);
        updatePredicatePanes();
    }
}

