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

import com.fasterxml.jackson.annotation.JsonIgnore;
import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import dslabs.framework.testing.ClientWorker;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import dslabs.framework.testing.newviz.BaseTree.BaseTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import jiconfont.IconCode;
import jiconfont.icons.font_awesome.FontAwesome;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ClassUtils;

import static dslabs.framework.testing.newviz.Utils.makeIcon;

// TODO: add a ton of tests for this...

enum TreeDisplayType {
    NEW("new"), HIGHLIGHT("highlight"), LOWLIGHT("lowlight"), DEFAULT(null);

    final String styleClass;

    TreeDisplayType(String styleClass) {
        this.styleClass = styleClass;
    }
}


class BaseTree extends JTree {
    @Setter private Icon rootIcon = null;

    protected static class BaseTreeCellRenderer
            extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel,
                                                      boolean expanded,
                                                      boolean leaf, int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf,
                    row, hasFocus);
            assert tree instanceof BaseTree;
            final BaseTree t = (BaseTree) tree;
            setIcon(t.rootIcon);
            return this;
        }
    }

    BaseTree() {
        super();
        setupTree();
    }

    BaseTree(TreeNode root) {
        super(root);
        setupTree();
    }

    private void setupTree() {
        setFocusable(false);
        setSelectionModel(null);

        setCellRenderer(new BaseTreeCellRenderer());

        // Might want root handles, but they are somewhat ugly
        // setShowsRootHandles(true);
    }

    void setTreeDisplayType(TreeDisplayType treeDisplayType) {
        putClientProperty("FlatLaf.styleClass", treeDisplayType.styleClass);
    }
}


class StateTree extends BaseTree {
    private final StateTreeNode root;

    StateTree(Object obj) {
        super();
        root = StateTreeNode.createNode(obj);
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        setModel(treeModel);
        setCellRenderer(new StateTreeCellRenderer());
    }

    void update(Object newObject) {
        final Object oldRoot = root.valueObj();
        final StateTreeNode newRoot = StateTreeNode.createNode(newObject);
        root.update(newRoot, (DefaultTreeModel) treeModel);
        // TODO: ultra hacky
        root.setDiffObject(StateTreeNode.createNode(oldRoot),
                (DefaultTreeModel) treeModel);
    }

    void update(Object newObject, Object diffTarget) {
        root.update(StateTreeNode.createNode(newObject),
                (DefaultTreeModel) treeModel);
        root.setDiffObject(StateTreeNode.createNode(diffTarget),
                (DefaultTreeModel) treeModel);
    }

    void clearDiffObject() {
        root.clearDiffObject((DefaultTreeModel) treeModel);
    }
}


class StateTreeCellRenderer extends BaseTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                  boolean sel, boolean expanded,
                                                  boolean leaf, int row,
                                                  boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf,
                row, hasFocus);
        assert tree instanceof StateTree;
        assert value instanceof StateTreeNode;

        final StateTreeNode n = (StateTreeNode) value;

        // TODO: pick the color based on node state
        IconCode iconCode = n.icon();
        if (iconCode != null) {
            setIcon(makeIcon(iconCode));
        } else {
            setIcon(null);
        }

        setText(n.treeCellText());

        switch (n.diffStatus()) {
            case NEW:
                setBackgroundColorFromKey("newTreeCellStateColor");
                break;
            case CHANGED:
                setBackgroundColorFromKey("changedTreeCellStateColor");
                break;
            case UNCHANGED:
            case NOT_DIFFED:
                setBackgroundColorFromKey(null);
                break;
        }

        return this;
    }

    private void setBackgroundColorFromKey(String key) {
        if (key == null) {
            setBackground(null);
            setOpaque(false);
        } else {
            Color color = UIManager.getColor(key);
            // Make a copy of the color because DefaultTreeCellRenderer ignores ColorUIResource from UIManager
            color = new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    color.getAlpha());
            setBackground(color);
            setOpaque(true);
        }
    }
}


/**
 * Custom JTree node representing some piece of state in a DSLabs node,
 * MessageEnvelope, or Timer. Tree nodes expand as their children are queried;
 * this allows for circular references in object graphs.
 *
 * Sub-classes should implement a {@code static boolean canHandle(Object)}
 * method and should be listed in {@link #NODE_TYPES_IN_PRIORITY_ORDER}. They
 * should also implement a constructor with arguments Object, Object.
 *
 * All state necessary for DSLabsTreeNodes should be encoded in the key and
 * value; sub-classes should not have any extra fields. If nodes expand and
 * create children, children should only be added through the addChild method
 * below.
 */
abstract class StateTreeNode extends DefaultMutableTreeNode {
    private static final Class<?>[] NODE_TYPES_IN_PRIORITY_ORDER =
            {PrimitiveNode.class, BoxedPrimitiveNode.class, StringNode.class,
                    ListNode.class, MapNode.class, SetNode.class,
                    AddressNode.class, DefaultObjectNode.class};

    @SneakyThrows
    static protected StateTreeNode createNode(Object value) {
        if (value == null) {
            return new DefaultObjectNode(null);
        }
        return createNode(value.getClass(), value);
    }

    @SneakyThrows
    static protected StateTreeNode createNode(Class<?> clz, Object value) {
        for (Class<?> c : NODE_TYPES_IN_PRIORITY_ORDER) {
            if ((Boolean) c.getDeclaredMethod("canHandle", Class.class)
                           .invoke(null, clz)) {
                return (StateTreeNode) c.getDeclaredConstructor(Object.class)
                                        .newInstance(value);
            }
        }
        throw new RuntimeException("Couldn't create node for object: " + value);
    }

    private interface ChildKey {
        Object key();

        String renderKey();
    }

    @Data
    private static class DefaultChildKey implements ChildKey {
        private final Object key;

        @Override
        public String renderKey() {
            return String.format("%s=", key);
        }
    }

    /**
     * Represents the nth occurrence of a key object in the children vector,
     * where n starts with 0.
     */
    @Data
    private static class KeyInstance {
        @NonNull private final ChildKey key;
        @Getter(AccessLevel.NONE) private final int i;
    }

    enum DiffStatus {
        UNCHANGED, CHANGED, NEW, NOT_DIFFED
    }

    private KeyInstance keyInstance;
    @Getter(AccessLevel.PROTECTED) private Object valueObj;
    private StateTreeNode diffTarget;
    private boolean isDiffed = false;
    private boolean hasExpanded = false;

    protected StateTreeNode(Object value) {
        valueObj = value;
    }

    protected String renderKey() {
        if (keyInstance == null) {
            return null;
        }
        return keyInstance.key().renderKey();
    }

    protected Object keyObj() {
        if (keyInstance == null) {
            return null;
        }
        return keyInstance.key().key();
    }

    /**
     * Updates the tree nodes with values and children from a new tree. Notifies
     * treeModel of changes along the way. Destroys newNode and all its
     * children.
     *
     * @param newNode
     * @param treeModel
     */
    final void update(StateTreeNode newNode, DefaultTreeModel treeModel) {
        assert Objects.equals(keyInstance, newNode.keyInstance);
        Object oldValueObject = valueObj;
        valueObj = newNode.valueObj;
        if (!Objects.equals(oldValueObject, newNode.valueObj)) {
            treeModel.nodeChanged(this);
        }

        assert !newNode.hasExpanded;
        if (!hasExpanded) {
            return;
        }
        newNode.expandInternal();

        if (children != null) {
            final List<MutableTreeNode> toRemove = new ArrayList<>();
            final Map<KeyInstance, StateTreeNode> newChildren =
                    newNode.childMap();

            for (TreeNode c : children) {
                assert c instanceof StateTreeNode;
                StateTreeNode n = (StateTreeNode) c;
                if (!newChildren.containsKey(n.keyInstance)) {
                    toRemove.add(n);
                } else {
                    n.update(newChildren.get(n.keyInstance), treeModel);
                }
            }

            for (MutableTreeNode n : toRemove) {
                treeModel.removeNodeFromParent(n);
            }
        }

        // XXX: preserve new node's sorted order of children???

        if (newNode.children != null) {
            final Map<KeyInstance, StateTreeNode> oldChildren = childMap();
            for (TreeNode c : newNode.children) {
                assert c instanceof StateTreeNode;
                StateTreeNode n = (StateTreeNode) c;
                if (!oldChildren.containsKey(n.keyInstance)) {
                    n.parent = null;
                    treeModel.insertNodeInto(n, this, getChildCount());
                }
            }
        }
    }

    void setDiffObject(StateTreeNode node, DefaultTreeModel treeModel) {
        isDiffed = true;

        if (node == null) {
            diffTarget = null;
            if (treeModel != null) {
                treeModel.nodeChanged(this);
            }
            if (children != null) {
                for (TreeNode c : children) {
                    StateTreeNode n = (StateTreeNode) c;
                    n.setDiffObject(null, treeModel);
                }
            }

            return;
        }

        assert Objects.equals(keyInstance, node.keyInstance);
        assert node.diffTarget == null;
        assert !node.isDiffed;

        diffTarget = node;
        // TODO: don't always call changed
        if (treeModel != null) {
            treeModel.nodeChanged(this);
        }

        if (children != null) {
            node.expandInternal();
            Map<KeyInstance, StateTreeNode> diffChildren = node.childMap();
            for (TreeNode c : children) {
                StateTreeNode n = (StateTreeNode) c;
                n.setDiffObject(diffChildren.getOrDefault(n.keyInstance, null),
                        treeModel);
            }
        }
    }

    void clearDiffObject(DefaultTreeModel treeModel) {
        diffTarget = null;
        isDiffed = false;

        // TODO: don't always change
        treeModel.nodeChanged(this);

        if (children != null) {
            // TODO: pattern for iterating children...
            for (TreeNode c : children) {
                StateTreeNode n = (StateTreeNode) c;
                n.clearDiffObject(treeModel);
            }
        }
    }

    final IconCode icon() {
        if (getParent() != null) {
            return null;
        }

        if (valueObj instanceof ClientWorker || valueObj instanceof Client) {
            return FontAwesome.USER;
        }

        if (valueObj instanceof Node) {
            return FontAwesome.DESKTOP;
        }

        if (valueObj instanceof MessageEnvelope ||
                valueObj instanceof Message) {
            return FontAwesome.ENVELOPE;
        }

        if (valueObj instanceof TimerEnvelope || valueObj instanceof Timer) {
            return FontAwesome.CLOCK_O;
        }

        return FontAwesome.LIST_UL;
    }

    final DiffStatus diffStatus() {
        if (!isDiffed) {
            return DiffStatus.NOT_DIFFED;
        }

        if (diffTarget == null) {
            return DiffStatus.NEW;
        }

        if (!Objects.equals(valueObj, diffTarget.valueObj)) {
            return DiffStatus.CHANGED;
        }

        return DiffStatus.UNCHANGED;
    }

    protected static String secondaryColor() {
        return Utils.colorToHex(UIManager.getColor("Tree.textSecondary"));
    }

    final String treeCellText() {
        return "<html>" + treeCellTextInternal() + "</html>";
    }

    protected String treeCellTextInternal() {

        if (renderKey() == null && valueObj() != null &&
                (valueObj() instanceof ClientWorker ||
                        valueObj() instanceof MessageEnvelope ||
                        valueObj() instanceof TimerEnvelope)) {
            return valueObj().toString();
        }

        StringBuilder sb = new StringBuilder();
        if (renderKey() != null) {
            sb.append(renderKey());
        }
        if (valueObj() != null) {
            sb.append(String.format("<font color='%s'>(%s)</font>",
                    secondaryColor(), valueObj().getClass().getSimpleName()));
            sb.append(valueObj());
        } else {
            sb.append(String.format("<font color='%s'>null</font>",
                    secondaryColor()));
        }

        return sb.toString();
    }

    private Map<KeyInstance, StateTreeNode> childMap() {
        if (children == null) {
            return Collections.emptyMap();
        }
        return children.stream().collect(
                Collectors.toMap(n -> ((StateTreeNode) n).keyInstance,
                        n -> ((StateTreeNode) n)));
    }

    private void expandInternal() {
        if (hasExpanded) {
            return;
        }

        final Map<ChildKey, Integer> keyOccurrences = new HashMap<>();
        expand((key, child) -> {
            assert allowsChildren;
            assert child != null;
            assert child.getParent() == null;
            assert child.keyInstance == null;

            // First setup the child's key
            int i = keyOccurrences.getOrDefault(key, 0);
            child.keyInstance = new KeyInstance(key, i);
            child.setUserObject(key);
            keyOccurrences.put(key, i + 1);

            // Take care of the super class
            child.setParent(this);
            if (children == null) {
                children = new Vector<>();
            }
            children.add(child);
        });
        hasExpanded = true;

        // TODO: kind of hacky
        if (isDiffed) {
            setDiffObject(diffTarget, null);
        }
    }

    protected abstract void expand(
            BiConsumer<ChildKey, StateTreeNode> childAdder);

    /* Override default interface methods to expand nodes on demand. */
    @Override
    public final TreeNode getChildAt(int index) {
        expandInternal();
        return super.getChildAt(index);
    }

    @Override
    public final int getChildCount() {
        expandInternal();
        return super.getChildCount();
    }

    @Override
    public final boolean isLeaf() {
        expandInternal();
        return super.isLeaf();
    }

    @Override
    public final Enumeration<TreeNode> children() {
        expandInternal();
        return super.children();
    }


    private static class DefaultObjectNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return true;
        }

        DefaultObjectNode(Object value) {
            super(value);
        }

        @SneakyThrows
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
            final Object valueObj = valueObj();

            if (valueObj == null) {
                return;
            }

            // TODO: fix illegal reflective access somehow??? (probably just add --add-opens to intellij default config for launching apps)
            for (Class<?> c = valueObj.getClass();
                 c != Object.class && c != null; c = c.getSuperclass()) {
                for (final Field f : c.getDeclaredFields()) {
                    final int modifiers = f.getModifiers();
                    if (Modifier.isStatic(modifiers)) {
                        continue;
                    }
                    if (Modifier.isTransient(modifiers)) {
                        continue;
                    }
                    // TODO: add annotation for viz to ignore field
                    if (f.getAnnotation(JsonIgnore.class) != null ||
                            f.getAnnotation(VizIgnore.class) != null) {
                        continue;
                    }
                    // XXX: is canAccess instead of isAccessible correct?
                    if (!f.canAccess(valueObj)) {
                        f.setAccessible(true);
                    }
                    final Object fieldVal = f.get(valueObj);

                    // TODO: move this check somewhere else...
                    if (valueObj instanceof Node &&
                            f.getName().equals("subNodes") &&
                            fieldVal != null &&
                            ((HashMap<?, ?>) fieldVal).isEmpty()) {
                        continue;
                    }

                    childAdder.accept(new DefaultChildKey(f.getName()),
                            createNode(f.getType(), fieldVal));
                }
            }
        }
    }


    private static final class MapNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return Map.class.isAssignableFrom(clz);
        }

        MapNode(Object value) {
            super(value);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
            for (Entry<?, ?> entry : ((Map<?, ?>) valueObj()).entrySet()) {
                childAdder.accept(new DefaultChildKey(entry.getKey()),
                        new MapEntryNode(entry.getValue()));
            }
        }

        private static class MapEntryNode extends StateTreeNode {
            protected MapEntryNode(Object value) {
                super(value);
            }

            @Override
            protected void expand(
                    BiConsumer<ChildKey, StateTreeNode> childAdder) {
                childAdder.accept(new DefaultChildKey("key"),
                        createNode(keyObj()));
                childAdder.accept(new DefaultChildKey("value"),
                        createNode(valueObj()));
            }

            @Override
            protected String treeCellTextInternal() {
                assert keyObj() != null;

                // TODO: find better arrow symbol; also make sure font consistent across OSes

                if (valueObj() == null) {
                    return String.format(
                            "<font color='%s'>(%s)</font>%s → <font color='%s'>null</font>",
                            secondaryColor(),
                            keyObj().getClass().getSimpleName(), keyObj(),
                            secondaryColor());
                }

                return String.format(
                        "<font color='%s'>(%s)</font>%s → <font color='%s'>(%s)</font>%s",
                        secondaryColor(), keyObj().getClass().getSimpleName(),
                        keyObj(), secondaryColor(),
                        valueObj().getClass().getSimpleName(), valueObj());
            }
        }
    }


    private static final class ListNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return List.class.isAssignableFrom(clz);
        }

        @Data
        private static class ListKey implements ChildKey {
            @NonNull private final Integer key;

            @Override
            public String renderKey() {
                return String.format("[%s]:", key);
            }
        }

        ListNode(Object value) {
            super(value);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
            int i = 0;
            for (Object item : (List<?>) valueObj()) {
                childAdder.accept(new ListKey(i), createNode(item));
                i++;
            }
        }
    }


    private static final class SetNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return Set.class.isAssignableFrom(clz);
        }

        SetNode(Object value) {
            super(value);
        }

        @Data
        private static class SetKey implements ChildKey {
            @NonNull private final Object key;

            @Override
            public String renderKey() {
                return "";
            }
        }

        @Override
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
            for (Object item : (Set<?>) valueObj()) {
                childAdder.accept(new SetKey(item), createNode(item));
            }
        }
    }


    // TODO: handle non-boxed primitives (and render them properly) somehow??
    // is this even necessary?
    private static final class BoxedPrimitiveNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isAssignableFrom(Byte.class) ||
                    clz.isAssignableFrom(Short.class) ||
                    clz.isAssignableFrom(Integer.class) ||
                    clz.isAssignableFrom(Long.class) ||
                    clz.isAssignableFrom(Double.class) ||
                    clz.isAssignableFrom(Float.class) ||
                    clz.isAssignableFrom(Boolean.class) ||
                    clz.isAssignableFrom(Character.class);
        }

        BoxedPrimitiveNode(Object value) {
            super(value);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
        }
    }


    private static final class PrimitiveNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isPrimitive();
        }

        PrimitiveNode(Object value) {
            super(value);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
        }

        @Override
        protected String treeCellTextInternal() {
            Class<?> primitiveClass =
                    ClassUtils.wrapperToPrimitive(valueObj().getClass());

            StringBuilder sb = new StringBuilder();
            if (keyObj() != null) {
                sb.append(renderKey());
            }
            sb.append(String.format("<font color='%s'>(%s)</font>",
                    secondaryColor(), primitiveClass.getSimpleName()));
            sb.append(valueObj());

            return sb.toString();
        }
    }


    private static final class StringNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isAssignableFrom(String.class);
        }

        StringNode(Object value) {
            super(value);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
        }
    }


    private static final class AddressNode extends StateTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isAssignableFrom(Address.class);
        }

        AddressNode(Object value) {
            super(value);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, StateTreeNode> childAdder) {
        }

        @Override
        protected String treeCellTextInternal() {
            assert valueObj() != null && valueObj() instanceof Address;

            StringBuilder sb = new StringBuilder();
            if (keyObj() != null) {
                sb.append(renderKey());
            }
            sb.append(String.format("<font color='%s'>(Address)</font>",
                    secondaryColor()));
            sb.append(valueObj());

            return sb.toString();
        }
    }
}
