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

import dslabs.framework.Address;
import dslabs.framework.Client;
import dslabs.framework.Message;
import dslabs.framework.Node;
import dslabs.framework.Timer;
import dslabs.framework.VizIgnore;
import dslabs.framework.testing.ClientWorker;
import dslabs.framework.testing.MessageEnvelope;
import dslabs.framework.testing.TimerEnvelope;
import java.awt.Color;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
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

import static dslabs.framework.testing.visualization.Utils.makeIcon;


enum JTreeDisplayType {
    NEW("new"), HIGHLIGHT("highlight"), LOWLIGHT("lowlight"), DEFAULT(null);

    final String styleClass;

    JTreeDisplayType(String styleClass) {
        this.styleClass = styleClass;
    }
}


/**
 * Base JTree subclass which allows setting the style of the tree and setting
 * the tree's root node icon.
 */
class BaseJTree extends JTree {
    @Setter private Icon rootIcon = null;

    protected static class BaseJTreeCellRenderer
            extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean sel,
                                                      boolean expanded,
                                                      boolean leaf, int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf,
                    row, hasFocus);
            assert tree instanceof BaseJTree;
            final BaseJTree t = (BaseJTree) tree;
            setIcon(t.rootIcon);
            return this;
        }
    }

    BaseJTree() {
        super();
        setupTree();
    }

    BaseJTree(TreeNode root) {
        super(root);
        setupTree();
    }

    private void setupTree() {
        setFocusable(false);
        setSelectionModel(null);

        setCellRenderer(new BaseJTreeCellRenderer());

        // Might want root handles, but they are somewhat ugly
        // setShowsRootHandles(true);
    }

    void setTreeDisplayType(JTreeDisplayType jTreeDisplayType) {
        putClientProperty("FlatLaf.styleClass", jTreeDisplayType.styleClass);
    }
}


/**
 * Specialized JTree which displays arbitrary Java objects. These objects can be
 * arbitrarily nested and can even contain circular references. Objects are
 * expanded on demand. Objects given to a {@code JObjectTree} should not be
 * modified in any after creation of the tree.
 */
class ObjectJTree extends BaseJTree {
    private static class ObjectJTreeCellRenderer extends BaseJTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean selected,
                                                      boolean expanded,
                                                      boolean leaf, int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded,
                    leaf, row, hasFocus);
            assert tree instanceof ObjectJTree;
            assert value instanceof ObjectTreeNode;

            final ObjectTreeNode n = (ObjectTreeNode) value;

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
                if (color != null) {
                    // Make a copy of the color because DefaultTreeCellRenderer ignores ColorUIResource from UIManager
                    color = new Color(color.getRed(), color.getGreen(),
                            color.getBlue(), color.getAlpha());
                }
                setBackground(color);
                setOpaque(true);
            }
        }
    }

    private final ObjectTreeNode root;

    /**
     * If true, message sender information and the message wrapper isn't
     * displayed.
     */
    @Setter(AccessLevel.PACKAGE) private boolean stripMessageDestination =
            false;

    ObjectJTree(Object obj) {
        super();
        root = ObjectTreeNode.createNode(obj, this);
        DefaultTreeModel treeModel = new DefaultTreeModel(root);
        setModel(treeModel);
        setCellRenderer(new ObjectJTreeCellRenderer());
    }

    /**
     * Change the object displayed by this tree. The tree will display the new
     * object and highlight its diff with the previous object.
     *
     * @param newObject
     *         the new object to render in this tree
     */
    void update(Object newObject) {
        final Object oldRoot = root.valueObj();
        final ObjectTreeNode newRoot =
                ObjectTreeNode.createNode(newObject, this);
        root.update(newRoot, (DefaultTreeModel) treeModel);
        // TODO: ultra hacky
        root.setDiffObject(ObjectTreeNode.createNode(oldRoot, this),
                (DefaultTreeModel) treeModel);
    }

    /**
     * Change the object displayed by this tree. The tree will display the new
     * object and highlight its diff with the diff target.
     *
     * @param newObject
     *         the new object to render in this tree
     * @param diffTarget
     *         the object to diff with the new root object
     */
    void update(Object newObject, Object diffTarget) {
        root.update(ObjectTreeNode.createNode(newObject, this),
                (DefaultTreeModel) treeModel);
        root.setDiffObject(ObjectTreeNode.createNode(diffTarget, this),
                (DefaultTreeModel) treeModel);
    }

    /**
     * Clear all diffing in this tree.
     */
    void clearDiffObject() {
        root.clearDiffObject((DefaultTreeModel) treeModel);
    }

    /*--------------------------------------------------------------------------
     * Base ObjectTreeNode
     -------------------------------------------------------------------------*/

    /**
     * Custom JTree node representing some piece of state in a DSLabs node,
     * MessageEnvelope, or Timer. Tree nodes expand as their children are
     * queried; this allows for circular references in object graphs.
     *
     * Sub-classes should implement a {@code static boolean canHandle(Object)}
     * method and should be listed in {@link #NODE_TYPES_IN_PRIORITY_ORDER}.
     * They should also implement a constructor with arguments Object, Object.
     *
     * All state necessary for DSLabsTreeNodes should be encoded in the key and
     * value; sub-classes should not have any extra fields. If nodes expand and
     * create children, children should only be added through the addChild
     * method below.
     */
    private static abstract class ObjectTreeNode
            extends DefaultMutableTreeNode {
        private static final Class<?>[] NODE_TYPES_IN_PRIORITY_ORDER =
                {ArrayNode.class, PrimitiveNode.class, BoxedPrimitiveNode.class,
                        StringNode.class, ListNode.class, MapNode.class,
                        SetNode.class, AddressNode.class,
                        DefaultObjectNode.class};

        @SneakyThrows
        static protected ObjectTreeNode createNode(Object value,
                                                   ObjectJTree tree) {
            if (value == null) {
                return new DefaultObjectNode(null, tree);
            }
            return createNode(value.getClass(), value, tree);
        }

        @SneakyThrows
        static protected ObjectTreeNode createNode(Class<?> clz, Object value,
                                                   ObjectJTree tree) {
            // Non-default tree nodes don't handle null values
            if (value == null) {
                return new DefaultObjectNode(null, tree);
            }

            for (Class<?> c : NODE_TYPES_IN_PRIORITY_ORDER) {
                if ((Boolean) c.getDeclaredMethod("canHandle", Class.class)
                               .invoke(null, clz)) {
                    return (ObjectTreeNode) c.getDeclaredConstructor(
                                                     Object.class, ObjectJTree.class)
                                             .newInstance(value, tree);
                }
            }
            throw new RuntimeException(
                    "Couldn't create node for object: " + value);
        }

        @SneakyThrows
        protected ObjectTreeNode createNode(Object value) {
            return createNode(value, this.tree);
        }

        @SneakyThrows
        protected ObjectTreeNode createNode(Class<?> clz, Object value) {
            return createNode(clz, value, this.tree);
        }

        protected interface ChildKey {
            Object key();

            String renderKey();
        }

        @Data
        static protected class DefaultChildKey implements ChildKey {
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
        private ObjectTreeNode diffTarget;
        private boolean isDiffed = false;
        private boolean hasExpanded = false;

        /**
         * A reference to the parent tree. Useful for grabbing custom settings
         * from the enclosing ObjectJTree.
         */
        @Getter(AccessLevel.PROTECTED)
        private final ObjectJTree tree;

        protected ObjectTreeNode(Object value, ObjectJTree tree) {
            valueObj = value;
            this.tree = tree;
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
         * Updates the tree nodes with values and children from a new tree.
         * Notifies treeModel of changes along the way. Destroys newNode and all
         * its children.
         *
         * @param newNode
         *         the node to take value objects from
         * @param treeModel
         *         the tree model to notify of changes
         */
        final void update(ObjectTreeNode newNode, DefaultTreeModel treeModel) {
            assert Objects.equals(keyInstance, newNode.keyInstance);
            final Object oldValueObject = valueObj;
            valueObj = newNode.valueObj;
            if (!Objects.deepEquals(oldValueObject, newNode.valueObj)) {
                treeModel.nodeChanged(this);
            }

            assert !newNode.hasExpanded;
            if (!hasExpanded) {
                return;
            }
            newNode.expandInternal();

            if (children != null) {
                final List<MutableTreeNode> toRemove = new ArrayList<>();
                final Map<KeyInstance, ObjectTreeNode> newChildren =
                        newNode.childMap();

                for (ObjectTreeNode n : iterableChildren()) {
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
                final Map<KeyInstance, ObjectTreeNode> oldChildren = childMap();
                for (ObjectTreeNode n : newNode.iterableChildren()) {
                    if (!oldChildren.containsKey(n.keyInstance)) {
                        n.parent = null;
                        treeModel.insertNodeInto(n, this, getChildCount());
                    }
                }
            }
        }

        void setDiffObject(ObjectTreeNode diffNode,
                           DefaultTreeModel treeModel) {
            final ObjectTreeNode oldDiffTarget = diffTarget;
            final boolean wasDiffed = isDiffed;

            isDiffed = true;
            diffTarget = diffNode;

            if (diffTarget == null) {
                if (treeModel != null &&
                        (!wasDiffed || oldDiffTarget != null)) {
                    treeModel.nodeChanged(this);
                }
                for (ObjectTreeNode n : iterableChildren()) {
                    n.setDiffObject(null, treeModel);
                }
                return;
            }

            assert Objects.equals(keyInstance, diffTarget.keyInstance);
            assert diffTarget.diffTarget == null;
            assert !diffTarget.isDiffed;

            // TODO: don't always call changed
            if (treeModel != null) {
                treeModel.nodeChanged(this);
            }

            if (!hasExpanded) {
                return;
            }

            diffTarget.expandInternal();
            Map<KeyInstance, ObjectTreeNode> diffChildren =
                    diffTarget.childMap();
            for (ObjectTreeNode n : iterableChildren()) {
                n.setDiffObject(diffChildren.getOrDefault(n.keyInstance, null),
                        treeModel);
            }
        }

        void clearDiffObject(DefaultTreeModel treeModel) {
            if (!isDiffed) {
                return;
            }

            diffTarget = null;
            isDiffed = false;
            treeModel.nodeChanged(this);

            for (ObjectTreeNode n : iterableChildren()) {
                n.clearDiffObject(treeModel);
            }
        }

        /**
         * Use to iterate through the current children vector. Does not expand
         * the node.
         *
         * @return an iterable of child nodes
         */
        private Iterable<ObjectTreeNode> iterableChildren() {
            if (children == null) {
                return Collections.emptyList();
            }
            return children.stream().map(t -> {
                assert t instanceof ObjectTreeNode;
                return (ObjectTreeNode) t;
            }).collect(Collectors.toList());
        }

        final IconCode icon() {
            if (getParent() != null) {
                return null;
            }

            if (valueObj instanceof ClientWorker ||
                    valueObj instanceof Client) {
                return FontAwesome.USER;
            }

            if (valueObj instanceof Node) {
                return FontAwesome.DESKTOP;
            }

            if (valueObj instanceof MessageEnvelope ||
                    valueObj instanceof Message) {
                return FontAwesome.ENVELOPE;
            }

            if (valueObj instanceof TimerEnvelope ||
                    valueObj instanceof Timer) {
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

            if (!Objects.deepEquals(valueObj, diffTarget.valueObj)) {
                return DiffStatus.CHANGED;
            }

            return DiffStatus.UNCHANGED;
        }

        protected static String secondaryColor() {
            Color c = UIManager.getColor("Tree.textSecondary");
            if (c == null) {
                return "#000000";
            }
            return Utils.colorToHex(c);
        }

        final String treeCellText() {
            return "<html>" + treeCellTextInternal() + "</html>";
        }

        protected String treeCellTextInternal() {
            final Object vo = valueObj();

            if (isRoot() && (vo instanceof ClientWorker ||
                    vo instanceof MessageEnvelope ||
                    vo instanceof TimerEnvelope || vo instanceof Timer)) {

                if (vo instanceof MessageEnvelope &&
                        tree.stripMessageDestination) {
                    return String.format("%s ⇨ %s",
                            ((MessageEnvelope) vo).from(),
                            ((MessageEnvelope) vo).message());
                }

                if (vo instanceof MessageEnvelope) {
                    return String.format("%s ⇨ %s | %s",
                            ((MessageEnvelope) vo).from(),
                            ((MessageEnvelope) vo).to(),
                            ((MessageEnvelope) vo).message());
                }

                if (vo instanceof TimerEnvelope) {
                    return String.format("⇨ %s | %s", ((TimerEnvelope) vo).to(),
                            ((TimerEnvelope) vo).timer());
                }

                return vo.toString();
            }

            StringBuilder sb = new StringBuilder();
            if (renderKey() != null) {
                sb.append(renderKey());
            }
            if (vo != null) {
                sb.append(String.format("<font color='%s'>(%s)</font>",
                        secondaryColor(), vo.getClass().getSimpleName()));
                sb.append(vo);
            } else {
                sb.append(String.format("<font color='%s'>null</font>",
                        secondaryColor()));
            }

            return sb.toString();
        }

        private Map<KeyInstance, ObjectTreeNode> childMap() {
            if (children == null) {
                return Collections.emptyMap();
            }
            return children.stream().collect(
                    Collectors.toMap(n -> ((ObjectTreeNode) n).keyInstance,
                            n -> ((ObjectTreeNode) n)));
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
                BiConsumer<ChildKey, ObjectTreeNode> childAdder);

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
    }


    /*--------------------------------------------------------------------------
     * ObjectTreeNode sub-classes
     -------------------------------------------------------------------------*/

    private static class DefaultObjectNode extends ObjectTreeNode {
        static boolean canHandle(Class<?> clz) {
            return true;
        }

        DefaultObjectNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @SneakyThrows
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
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

                    if (f.getAnnotation(VizIgnore.class) != null) {
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


    private static final class MapNode extends ObjectTreeNode {
        static boolean canHandle(Class<?> clz) {
            return Map.class.isAssignableFrom(clz);
        }

        MapNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
            for (Entry<?, ?> entry : ((Map<?, ?>) valueObj()).entrySet()) {
                childAdder.accept(new DefaultChildKey(entry.getKey()),
                        new MapEntryNode(entry.getValue(), tree()));
            }
        }

        private static class MapEntryNode extends ObjectTreeNode {
            protected MapEntryNode(Object value, ObjectJTree tree) {
                super(value, tree);
            }

            @Override
            protected void expand(
                    BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
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


    private static final class ListNode extends ObjectTreeNode {
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

        ListNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
            int i = 0;
            for (Object item : (List<?>) valueObj()) {
                childAdder.accept(new ListKey(i), createNode(item));
                i++;
            }
        }
    }


    private static final class ArrayNode extends ObjectTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isArray();
        }

        @Data
        private static class ArrayKey implements ChildKey {
            @NonNull private final Integer key;

            @Override
            public String renderKey() {
                return String.format("[%s]:", key);
            }
        }

        @Override
        protected String treeCellTextInternal() {
            // TODO: repeated code from super
            StringBuilder sb = new StringBuilder();
            if (renderKey() != null) {
                sb.append(renderKey());
            }
            if (valueObj() != null) {
                sb.append(String.format("<font color='%s'>(%s)</font>",
                        secondaryColor(),
                        valueObj().getClass().getSimpleName()));

                Object o = valueObj();

                if (o instanceof byte[]) {
                    sb.append(Arrays.toString((byte[]) o));
                } else if (o instanceof short[]) {
                    sb.append(Arrays.toString((short[]) o));
                } else if (o instanceof int[]) {
                    sb.append(Arrays.toString((int[]) o));
                } else if (o instanceof long[]) {
                    sb.append(Arrays.toString((long[]) o));
                } else if (o instanceof char[]) {
                    sb.append(Arrays.toString((char[]) o));
                } else if (o instanceof float[]) {
                    sb.append(Arrays.toString((float[]) o));
                } else if (o instanceof double[]) {
                    sb.append(Arrays.toString((double[]) o));
                } else if (o instanceof boolean[]) {
                    sb.append(Arrays.toString((boolean[]) o));
                } else {
                    sb.append(Arrays.deepToString((Object[]) valueObj()));
                }
            } else {
                sb.append(String.format("<font color='%s'>null</font>",
                        secondaryColor()));
            }
            return sb.toString();
        }

        ArrayNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
            Object o = valueObj();
            int i = 0;
            if (o instanceof byte[]) {
                for (byte item : (byte[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(byte.class, item));
                    i++;
                }
            } else if (o instanceof short[]) {
                for (short item : (short[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(short.class, item));
                    i++;
                }
            } else if (o instanceof int[]) {
                for (int item : (int[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(int.class, item));
                    i++;
                }
            } else if (o instanceof long[]) {
                for (long item : (long[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(long.class, item));
                    i++;
                }
            } else if (o instanceof char[]) {
                for (char item : (char[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(char.class, item));
                    i++;
                }
            } else if (o instanceof float[]) {
                for (float item : (float[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(float.class, item));
                    i++;
                }
            } else if (o instanceof double[]) {
                for (double item : (double[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(double.class, item));
                    i++;
                }
            } else if (o instanceof boolean[]) {
                for (boolean item : (boolean[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i),
                            createNode(boolean.class, item));
                    i++;
                }
            } else {
                for (Object item : (Object[]) valueObj()) {
                    childAdder.accept(new ArrayKey(i), createNode(item));
                    i++;
                }
            }
        }
    }


    private static final class SetNode extends ObjectTreeNode {
        static boolean canHandle(Class<?> clz) {
            return Set.class.isAssignableFrom(clz);
        }

        SetNode(Object value, ObjectJTree tree) {
            super(value, tree);
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
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
            for (Object item : (Set<?>) valueObj()) {
                childAdder.accept(new SetKey(item), createNode(item));
            }
        }
    }


    private static final class BoxedPrimitiveNode extends ObjectTreeNode {
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

        BoxedPrimitiveNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
        }
    }


    private static final class PrimitiveNode extends ObjectTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isPrimitive();
        }

        PrimitiveNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
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


    private static final class StringNode extends ObjectTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isAssignableFrom(String.class);
        }

        StringNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
        }
    }


    private static final class AddressNode extends ObjectTreeNode {
        static boolean canHandle(Class<?> clz) {
            return clz.isAssignableFrom(Address.class);
        }

        AddressNode(Object value, ObjectJTree tree) {
            super(value, tree);
        }

        @Override
        protected void expand(BiConsumer<ChildKey, ObjectTreeNode> childAdder) {
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
