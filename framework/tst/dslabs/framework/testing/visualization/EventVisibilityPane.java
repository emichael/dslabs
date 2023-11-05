/*
 * Copyright (c) 2023 Ellis Michael
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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import dslabs.framework.Message;
import dslabs.framework.Timer;
import dslabs.framework.testing.Event;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jdesktop.swingx.JXTaskPane;


final class EventVisibilityPane extends JXTaskPane {

    private static final class EventTypeVisibilityPanel<E> extends JPanel {
        private static final String SHOW_ALL = "Show All", HIDE_ALL =
                "Hide All";

        private final SortedMap<Class<? extends E>, JCheckBox> checkBoxes =
                new TreeMap<>(Comparator.comparing(Class::getName));

        private final JButton toggle;

        private final Runnable onUpdate;

        private EventTypeVisibilityPanel(String eventTypePlural,
                                         Runnable onUpdate) {
            this.onUpdate = onUpdate;

            setVisible(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            add(new JLabel("<html><h3>" + eventTypePlural + "</h3></html>"));

            toggle = new JButton(HIDE_ALL);
            toggle.addActionListener(__ -> {
                if (allCheckBoxesSelected()) {
                    checkBoxes.values().forEach(c -> c.setSelected(false));
                    toggle.setText(SHOW_ALL);
                } else {
                    checkBoxes.values().forEach(c -> c.setSelected(true));
                    toggle.setText(HIDE_ALL);
                }
                onUpdate.run();
            });
            add(toggle);
        }

        private boolean allCheckBoxesSelected() {
            return checkBoxes.values().stream().allMatch(JCheckBox::isSelected);
        }

        void addCheckBox(Class<? extends E> eventType) {
            setVisible(true);

            if (!checkBoxes.containsKey(eventType)) {
                // Add 1 to account for the JLabel header
                int position = checkBoxes.headMap(eventType).size() + 1;
                JCheckBox checkBox = new JCheckBox(eventType.getSimpleName());
                checkBox.setToolTipText(eventType.getName());
                checkBox.setSelected(true);
                checkBox.addActionListener(__ -> {
                    toggle.setText(
                            allCheckBoxesSelected() ? HIDE_ALL : SHOW_ALL);
                    onUpdate.run();
                });
                add(checkBox, position);
                checkBoxes.put(eventType, checkBox);
            }
        }

        ImmutableSet<Class<? extends E>> hiddenEventTypes() {
            return checkBoxes.entrySet().stream()
                             .filter(e -> !e.getValue().isSelected())
                             .map(Entry::getKey)
                             .collect(ImmutableSet.toImmutableSet());
        }

        void reset() {
            setVisible(false);
            checkBoxes.values().forEach(this::remove);
            checkBoxes.clear();
            toggle.setText(HIDE_ALL);
        }
    }

    private final EventTypeVisibilityPanel<Message> messageVisibilityPanel;
    private final EventTypeVisibilityPanel<Timer> timerVisibilityPanel;

    EventVisibilityPane(Runnable onUpdate) {
        super("Show/Hide Events");
        setVisible(false);

        // TODO: figure out how to add this tooltip to the title only
        setToolTipText(
                "Show/hide events in Events list. Useful for filtering the " +
                        "visible events in long traces.");

        messageVisibilityPanel =
                new EventTypeVisibilityPanel<>("Messages", onUpdate);
        timerVisibilityPanel =
                new EventTypeVisibilityPanel<>("Timers", onUpdate);

        add(messageVisibilityPanel);
        add(timerVisibilityPanel);
    }

    @RequiredArgsConstructor
    @Getter
    @Immutable
    static final class HiddenEventClasses {
        static final HiddenEventClasses EMPTY =
                new HiddenEventClasses(ImmutableSet.of(), ImmutableSet.of());

        private final ImmutableSet<Class<? extends Message>> hiddenMessageTypes;
        private final ImmutableSet<Class<? extends Timer>> hiddenTimerTypes;

        boolean isHidden(Message m) {
            return hiddenMessageTypes.contains(m.getClass());
        }

        boolean isHidden(Timer t) {
            return hiddenTimerTypes.contains(t.getClass());
        }

        boolean isHidden(Event e) {
            return e.isMessage() ? isHidden(e.message().message()) :
                    isHidden(e.timer().timer());
        }
    }

    HiddenEventClasses hiddenEventClasses() {
        return new HiddenEventClasses(messageVisibilityPanel.hiddenEventTypes(),
                timerVisibilityPanel.hiddenEventTypes());
    }

    void addMessageCheckbox(Class<? extends Message> messageType) {
        setVisible(true);
        messageVisibilityPanel.addCheckBox(messageType);
    }

    void addTimerCheckbox(Class<? extends Timer> timerType) {
        setVisible(true);
        timerVisibilityPanel.addCheckBox(timerType);
    }

    void reset() {
        setVisible(false);
        messageVisibilityPanel.reset();
        timerVisibilityPanel.reset();
    }
}
