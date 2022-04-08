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

import com.google.common.collect.Sets;
import dslabs.framework.Address;
import dslabs.framework.testing.Event;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.search.SearchSettings;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import jiconfont.icons.font_awesome.FontAwesome;
import net.miginfocom.layout.AC;
import net.miginfocom.layout.LC;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import static javax.swing.JSplitPane.VERTICAL_SPLIT;

class SingleNodePanel extends JPanel {
    private final Address address;

    private final JPanel messageBox, timerBox;
    private final StateTree nodeState;
    private final DebuggerWindow parent;

    private final JSplitPane mainSplitPane, eventPane;

    private final Map<MessageEnvelope, Pair<JButton, StateTree>> messages =
            new HashMap<>();
    private final List<Triple<TimerEnvelope, JPanel, StateTree>> timers =
            new ArrayList<>();

    SingleNodePanel(final EventTreeState s, final SearchSettings settings,
                    final Address a, final DebuggerWindow parent) {
        this.parent = parent;
        address = a;

        setLayout(new MigLayout("fill, wrap 1"));

        eventPane = new JSplitPane(VERTICAL_SPLIT);

        messageBox = new JPanel(
                new MigLayout(new LC().wrapAfter(1), new AC().gap("0"),
                        new AC().gap("0")));
        JScrollPane scrollPane = new JScrollPane(messageBox);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Messages"));
        eventPane.add(scrollPane);

        timerBox = new JPanel(
                new MigLayout(new LC().wrapAfter(1), new AC().gap("0"),
                        new AC().gap("0")));
        scrollPane = new JScrollPane(timerBox);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Timers"));
        eventPane.add(scrollPane);
        eventPane.setResizeWeight(0.5);

        mainSplitPane = new JSplitPane(VERTICAL_SPLIT);
        mainSplitPane.setDividerLocation(0.5);
        mainSplitPane.add(eventPane);
        nodeState = new StateTree(s.node(a));
        scrollPane = new JScrollPane(nodeState);
        mainSplitPane.add(scrollPane);
        mainSplitPane.setResizeWeight(0.4);

        // XXX: why does this need w 100%, h 100%? Shouldn't grow handle it?
        add(mainSplitPane, "grow, h 100%");

        updateState(s, settings, false);

        add(new JLabel(a.toString()), "center");
    }

    // Gross but necessary hack to set the initial location of the dividers
    boolean painted = false;

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        if (!painted) {
            painted = true;
            mainSplitPane.setDividerLocation(0.4f);
            eventPane.setDividerLocation(0.5f);
        }
    }

    // TODO: do this _much_ more incrementally without repainting everything
    void updateState(EventTreeState s, SearchSettings settings,
                     boolean viewDeliveredMessages) {
        boolean nodeIsDiffed = !s.isInitialState() &&
                s.previousEvent().locationRootAddress().equals(address);
        if (nodeIsDiffed) {
            nodeState.update(s.node(address), s.parent().node(address));
        } else {
            // TODO: don't always update if we're coming from an event being fired
            nodeState.update(s.node(address));
            nodeState.clearDiffObject();
        }

        boolean pruned = settings != null && settings.shouldPrune(s.state());

        final Set<MessageEnvelope> ms = Sets.newHashSet(
                viewDeliveredMessages ? s.network() : s.undeliveredMessages());
        boolean repaintMessageBox = false;
        for (MessageEnvelope message : ms) {
            if (!message.to().rootAddress().equals(address)) {
                continue;
            }
            if (messages.containsKey(message)) {
                continue;
            }
            addMessage(message, pruned,
                    settings != null && !settings.shouldDeliver(message));
            repaintMessageBox = true;
        }
        for (MessageEnvelope message : new HashSet<>(messages.keySet())) {
            if (!ms.contains(message)) {
                messageBox.remove(messages.get(message).getLeft().getParent());
                repaintMessageBox = true;
                messages.remove(message);
            } else {
                setDeliverability(messages.get(message).getLeft(), pruned,
                        settings != null && !settings.shouldDeliver(message),
                        "message");
            }
        }
        if (repaintMessageBox) {
            messageBox.revalidate();
            messageBox.repaint();
        }
        for (Entry<MessageEnvelope, Pair<JButton, StateTree>> messageEntry : messages.entrySet()) {
            MessageEnvelope message = messageEntry.getKey();
            StateTree tree = messageEntry.getValue().getRight();
            if (!s.isInitialState() &&
                    s.messageIsNew(message, viewDeliveredMessages)) {
                tree.setTreeDisplayType(TreeDisplayType.NEW);
            } else {
                tree.setTreeDisplayType(TreeDisplayType.DEFAULT);
            }
        }

        /*
            TODO: do the same thing for timers...

            This is tricky, though. Messages are unique because of the network
            model. But for timers, there might be multiple copies of the same
            one in the queue. We need to diff the lists intelligently to make
            sure we only pickup the newly added timers. We know the new ones are
            at the back of the list, but if the most recent event was a timer
            delivery, that makes things nasty.

            Just updating the list with the new one is easy, but giving timers
            the correct TreeDisplayType is hard.
         */
        timerBox.removeAll();

        for (TimerEnvelope timer : s.timers(address)) {
            addTimer(timer, s.canStepTimer(timer), pruned,
                    settings != null && !settings.deliverTimers(address));
        }

        timerBox.revalidate();
        timerBox.repaint();
    }

    private void addMessage(final MessageEnvelope message, boolean pruned,
                            boolean prohibited) {
        final JPanel mbox =
                new JPanel(new MigLayout(null, null, new AC().align("top")));

        JButton deliveryButton =
                new JButton(Utils.makeIcon(FontAwesome.DOWNLOAD));
        deliveryButton.setFocusable(false);
        mbox.add(deliveryButton, "pad 0 0");
        deliveryButton.addActionListener(
                e -> parent.deliverEvent(new Event(message)));

        setDeliverability(deliveryButton, pruned, prohibited, "message");
        StateTree tree = new StateTree(message);
        tree.collapseRow(0);
        mbox.add(tree, "pad 0 0");

        messages.put(message, Pair.of(deliveryButton, tree));
        messageBox.add(mbox, "pad 0 0");
    }

    private void addTimer(final TimerEnvelope timer, boolean deliverable,
                          boolean pruned, boolean prohibited) {
        final JPanel tbox =
                new JPanel(new MigLayout(null, null, new AC().align("top")));

        final JButton deliveryButton =
                new JButton(Utils.makeIcon(FontAwesome.DOWNLOAD));
        deliveryButton.setFocusable(false);
        deliveryButton.addActionListener(
                e -> parent.deliverEvent(new Event(timer)));
        tbox.add(deliveryButton, "pad 0 0");

        if (!deliverable) {
            deliveryButton.setVisible(false);
        }

        setDeliverability(deliveryButton, pruned, prohibited, "timer");

        StateTree tree = new StateTree(timer.timer());
        tree.collapseRow(0);
        tbox.add(tree);

        timers.add(Triple.of(timer, tbox, tree));

        timerBox.add(tbox);
    }

    private void setDeliverability(JButton deliveryButton, boolean pruned,
                                   boolean prohibited, String name) {
        deliveryButton.setEnabled(!pruned && !prohibited);
        String tooltip;
        if (pruned) {
            tooltip = "This " + name +
                    " cannot be delivered because the current state is pruned by the search";
        } else if (prohibited) {
            tooltip = "This " + name +
                    " cannot be delivered because delivery is prohibited by the search";
        } else {
            tooltip = "Deliver " + name;
        }
        deliveryButton.setToolTipText(tooltip);
    }
}
