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
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
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
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import static javax.swing.JSplitPane.VERTICAL_SPLIT;

// TODO: add tests, especially for message and timer boxes

class SingleNodePanel extends JPanel {
    // Relative sizes of events panels (combined) to node and messages to timers
    private static final float EVENTS_PANEL_SIZE = 0.6f, MESSAGE_BOX_SIZE =
            0.6f;

    private final Address address;

    private final JPanel messageBox, timerBox;
    private final ObjectJTree nodeState;
    private final DebuggerWindow parent;

    private final JSplitPane mainSplitPane, eventPane;

    private final Map<MessageEnvelope, Pair<JButton, ObjectJTree>> messages =
            new HashMap<>();
    private final List<Triple<TimerEnvelope, JPanel, ObjectJTree>> timers =
            new ArrayList<>();

    SingleNodePanel(final EventTreeState s, final SearchSettings settings,
                    final Address a, final DebuggerWindow parent,
                    boolean viewDeliveredMessages) {
        this.parent = parent;
        address = a;

        setLayout(new MigLayout("fill, wrap 1"));

        eventPane = new JSplitPane(VERTICAL_SPLIT);

        messageBox = new JPanel(
                new MigLayout(new LC().wrapAfter(1), new AC().gap("0"),
                        new AC().gap("0")));
        JScrollPane scrollPane = Utils.scrollPane(messageBox);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Messages"));
        eventPane.add(scrollPane);

        timerBox = new JPanel(
                new MigLayout(new LC().wrapAfter(1), new AC().gap("0"),
                        new AC().gap("0")));
        scrollPane = Utils.scrollPane(timerBox);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Timers"));
        eventPane.add(scrollPane);
        eventPane.setResizeWeight(MESSAGE_BOX_SIZE);

        mainSplitPane = new JSplitPane(VERTICAL_SPLIT);
        mainSplitPane.add(eventPane);
        nodeState = new ObjectJTree(s.node(a));
        // TODO: expand the "client" element of the tree if node is a ClientWorker
        scrollPane = Utils.scrollPane(nodeState);
        mainSplitPane.add(scrollPane);
        mainSplitPane.setResizeWeight(EVENTS_PANEL_SIZE);

        // XXX: why does this need w 100%, h 100%? Shouldn't grow handle it?
        add(mainSplitPane, "grow, h 100%");

        updateState(s, settings, viewDeliveredMessages);

        add(new JLabel(a.toString()), "center");
    }

    // Gross but necessary hack to set the initial location of the dividers
    boolean painted = false;

    @Override
    public void paint(Graphics g) {
        super.paint(g);

        if (!painted) {
            painted = true;
            mainSplitPane.setDividerLocation(EVENTS_PANEL_SIZE);
            eventPane.setDividerLocation(MESSAGE_BOX_SIZE);
        }
    }

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

        final boolean pruned =
                settings != null && settings.shouldPrune(s.state());

        updateMessages(s, settings, viewDeliveredMessages, pruned);
        updateTimers(s, settings, pruned);
    }

    private void updateMessages(final EventTreeState s,
                                final SearchSettings settings,
                                final boolean viewDeliveredMessages,
                                final boolean pruned) {
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
                    settings != null && !settings.shouldDeliver(message),
                    s.thrownException() != null);
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
                        s.thrownException() != null, "message");
            }
        }
        if (repaintMessageBox) {
            messageBox.revalidate();
            messageBox.repaint();
        }
        for (Entry<MessageEnvelope, Pair<JButton, ObjectJTree>> messageEntry : messages.entrySet()) {
            MessageEnvelope message = messageEntry.getKey();
            ObjectJTree tree = messageEntry.getValue().getRight();
            if (!s.isInitialState() &&
                    s.messageIsNew(message, viewDeliveredMessages)) {
                tree.setTreeDisplayType(JTreeDisplayType.NEW);
            } else {
                tree.setTreeDisplayType(JTreeDisplayType.DEFAULT);
            }
        }
    }

    private void updateTimers(final EventTreeState s,
                              final SearchSettings settings,
                              final boolean pruned) {
        boolean repaintTimerBox = false;

        // Track numbers of times timers are seen to infer which ones are new
        Map<TimerEnvelope, Integer> otf = null, ctf = new HashMap<>(), stf =
                s.timerFrequencies(address);
        if (!s.isInitialState()) {
            otf = s.parent().timerFrequencies(address);
        }

        // The new timer queue to materialize to the user
        final var newTimers = Lists.newArrayList(s.timers(address).iterator());

        /*
            First, remove all the timers that aren't present in the new state,
            in reverse order so that the first copies of duplicates are removed
            (from the "head" of the queue).
         */
        for (int i = timers.size() - 1; i >= 0; i--) {
            var c = timers.get(i);
            var t = c.getLeft();
            final int tf = ctf.getOrDefault(t, 0) + 1;
            ctf.put(t, tf);
            if (tf > stf.getOrDefault(t, 0)) {
                // TODO (here and a few places below): n^2 removal
                timers.remove(i);
                timerBox.remove(c.getMiddle());
                repaintTimerBox = true;
            }
        }
        ctf = new HashMap<>();

        assert timers.size() <= newTimers.size();

        /*
            settings.canStepTimer(t) only tells us if a timer equal to t can be
            delivered. If there are duplicate timers in the queue, the result
            will be the same for all of them.

            However, the delivery rules for timers mean that if two identical
            timers (with identical durations) are in the queue, then only the
            first is actually deliverable.
         */
        final Set<TimerEnvelope> timerSet = new HashSet<>();

        int i = 0;
        while (i < newTimers.size()) {
            var t = newTimers.get(i);
            final int tf = ctf.getOrDefault(t, 0) + 1;
            final boolean tIsNew = otf != null && tf > otf.getOrDefault(t, 0);
            // This is too clever by half, but it works
            final Supplier<Boolean> tIsDeliverable =
                    () -> timerSet.add(t) && s.canStepTimer(t);

            var c = i < timers.size() ? timers.get(i) : null;

            if (c != null && Objects.equals(t, c.getLeft())) {
                JButton deliveryButton =
                        (JButton) c.getMiddle().getComponent(0);
                final boolean deliverable = tIsDeliverable.get();
                deliveryButton.setVisible(deliverable);
                if (deliverable) {
                    setDeliverability(deliveryButton, pruned,
                            settings != null && !settings.deliverTimers(t.to()),
                            s.thrownException() != null, "timer");
                }
                if (tIsNew) {
                    c.getRight().setTreeDisplayType(JTreeDisplayType.NEW);
                } else {
                    c.getRight().setTreeDisplayType(JTreeDisplayType.DEFAULT);
                }
                i++;
                ctf.put(t, tf);
                continue;
            }

            /*
                When there is a mismatch between the existing timer queue and
                the one we're trying to materialize, we need to decide whether
                this is an insertion or deletion (or substitution).

                The correct timer list will ultimately be displayed either way.
                However, if we remove and re-add a timer unnecessarily, this is
                bad for performance and also might cause a timer that should be
                left open to be "collapsed" from the user's perspective.

                For now, we just use a simple heuristic and insert the new timer
                when there are more new timers than current and remove the
                existing timer otherwise.
             */

            if (c == null || newTimers.size() > timers.size()) {
                // When there are more timers, insert t
                var r = timerPanel(t, tIsDeliverable.get(), pruned,
                        settings != null && !settings.deliverTimers(t.to()),
                        s.thrownException() != null, parent);
                timers.add(i, Triple.of(t, r.getLeft(), r.getRight()));
                timerBox.add(r.getLeft(), "pad 0 0", i);
                repaintTimerBox = true;
                if (tIsNew) {
                    r.getRight().setTreeDisplayType(JTreeDisplayType.NEW);
                }
                i++;
                ctf.put(t, tf);
            } else {
                // Otherwise, remove the existing timer
                timers.remove(i);
                timerBox.remove(c.getMiddle());
                repaintTimerBox = true;
            }
        }

        assert i >= timers.size();

        if (repaintTimerBox) {
            timerBox.revalidate();
            timerBox.repaint();
        }
    }

    private void addMessage(final MessageEnvelope message, boolean pruned,
                            boolean prohibited, boolean exception) {
        final JPanel mbox =
                new JPanel(new MigLayout(null, null, new AC().align("top")));

        JButton deliveryButton =
                new JButton(Utils.makeIcon(FontAwesome.DOWNLOAD));
        deliveryButton.setFocusable(false);
        mbox.add(deliveryButton, "pad 0 0");
        deliveryButton.addActionListener(
                e -> parent.deliverEvent(new Event(message)));

        setDeliverability(deliveryButton, pruned, prohibited, exception,
                "message");
        ObjectJTree tree = new ObjectJTree(message);
        tree.stripMessageDestination(true);
        tree.collapseRow(0);
        mbox.add(tree, "pad 0 0");

        messages.put(message, Pair.of(deliveryButton, tree));
        messageBox.add(mbox, "pad 0 0", 0);
    }

    private static Pair<JPanel, ObjectJTree> timerPanel(
            final TimerEnvelope timer, final boolean deliverable,
            final boolean pruned, final boolean prohibited, boolean exception,
            final DebuggerWindow parent) {
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

        setDeliverability(deliveryButton, pruned, prohibited, exception,
                "timer");

        ObjectJTree tree = new ObjectJTree(timer.timer());
        tree.collapseRow(0);
        tbox.add(tree);

        return new ImmutablePair<>(tbox, tree);
    }

    private static void setDeliverability(JButton deliveryButton,
                                          boolean pruned, boolean prohibited,
                                          boolean exception, String name) {

        deliveryButton.setEnabled(!pruned && !prohibited && !exception);
        String tooltip;
        if (exception) {
            tooltip = "This " + name +
                    " cannot be delivered because an exception was thrown";
        } else if (pruned) {
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
