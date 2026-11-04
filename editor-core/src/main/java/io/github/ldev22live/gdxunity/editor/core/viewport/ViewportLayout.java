package io.github.ldev22live.gdxunity.editor.core.viewport;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * How the 3D scene view is divided into panes (TD-039): a tree whose leaves are panes, each showing a
 * {@link ViewKind}, and whose inner nodes split an area in two, side by side or stacked, at a ratio the user can drag.
 * It starts as four panes (perspective, X, Y, Z) and can be split, closed, retyped, resized and maximized.
 *
 * <p>This is only the arrangement: it knows nothing about cameras or drawing. {@link #arrange} turns it into
 * rectangles for a given area, including the strips between panes that the user drags to resize them. Coordinates
 * have their origin at the bottom left with y up, like scene2d; the first child of a split is the left or top one.
 *
 * <p>Pane and split ids are small integers that stay the same while the layout is edited; they are not saved, and
 * loading assigns new ones. Not thread-safe.
 */
public final class ViewportLayout {
    /** Most panes a layout may hold. */
    public static final int MAX_PANES = 8;
    /** Smallest and largest share of a split's room the first child may get. */
    public static final float MIN_RATIO = 0.1f;
    /** Largest share of a split's room the first child may get. */
    public static final float MAX_RATIO = 0.9f;
    /** The size, in the layout's units, no pane is squeezed below by {@link #arrange} while there is room. */
    public static final float MIN_PANE_SIZE = 80f;
    /** Version written to and required in the JSON form. */
    static final int JSON_VERSION = 1;

    /** How a split divides its area. */
    public enum Orientation {
        /** The first pane is on the left, the second on the right. */
        SIDE_BY_SIDE,
        /** The first pane is on top, the second below. */
        STACKED
    }

    /** A pane of the layout. */
    public record PaneInfo(int id, ViewKind kind) {
    }

    /** Where a pane is, in the layout's coordinates (y up). */
    public record PaneRect(int paneId, ViewKind kind, float x, float y, float width, float height) {
    }

    /**
     * The strip between the two children of a split, which the user drags. {@code area*} is the whole area of the
     * split, needed to turn a pointer position into a ratio.
     */
    public record Divider(int splitId, Orientation orientation, float x, float y, float width, float height,
                          float areaX, float areaY, float areaWidth, float areaHeight) {
        /**
         * The ratio that puts the divider's centre at a pointer position, limited to {@link #MIN_RATIO} and
         * {@link #MAX_RATIO}.
         */
        public float ratioAt(float pointerX, float pointerY) {
            float gap = orientation == Orientation.SIDE_BY_SIDE ? width : height;
            float room = (orientation == Orientation.SIDE_BY_SIDE ? areaWidth : areaHeight) - gap;
            if (!(room > 0f)) {
                return 0.5f;
            }
            float offset = orientation == Orientation.SIDE_BY_SIDE
                ? pointerX - areaX
                : areaY + areaHeight - pointerY;
            return Math.max(MIN_RATIO, Math.min(MAX_RATIO, (offset - gap / 2f) / room));
        }
    }

    /** The panes and dividers for an area. */
    public record Arrangement(List<PaneRect> panes, List<Divider> dividers) {
    }

    private abstract static class Node {
        Split parent;
    }

    private static final class Leaf extends Node {
        final int id;
        ViewKind kind;

        Leaf(int id, ViewKind kind) {
            this.id = id;
            this.kind = kind;
        }
    }

    private static final class Split extends Node {
        final int id;
        final Orientation orientation;
        float ratio;
        Node first;
        Node second;

        Split(int id, Orientation orientation, float ratio, Node first, Node second) {
            this.id = id;
            this.orientation = orientation;
            this.ratio = ratio;
            this.first = first;
            this.second = second;
            first.parent = this;
            second.parent = this;
        }
    }

    private Node root;
    private int nextPaneId;
    private int nextSplitId;
    private int maximizedPaneId = -1;
    private long revision;

    private ViewportLayout(ViewKind kind) {
        root = new Leaf(nextPaneId++, Objects.requireNonNull(kind, "kind cannot be null"));
    }

    /** A layout with one pane. */
    public static ViewportLayout single(ViewKind kind) {
        return new ViewportLayout(kind);
    }

    /** Two panes side by side. */
    public static ViewportLayout sideBySide(ViewKind left, ViewKind right) {
        ViewportLayout layout = new ViewportLayout(left);
        layout.split(0, Orientation.SIDE_BY_SIDE, right);
        return layout;
    }

    /** Two panes, one above the other. */
    public static ViewportLayout stacked(ViewKind top, ViewKind bottom) {
        ViewportLayout layout = new ViewportLayout(top);
        layout.split(0, Orientation.STACKED, bottom);
        return layout;
    }

    /** The default 3D layout: perspective top left, X axis top right, Y axis bottom left, Z axis bottom right. */
    public static ViewportLayout quad() {
        ViewportLayout layout = new ViewportLayout(ViewKind.PERSPECTIVE);
        // Two rows first, then split each row into two columns
        int bottomLeft = layout.split(0, Orientation.STACKED, ViewKind.Y_AXIS);
        layout.split(0, Orientation.SIDE_BY_SIDE, ViewKind.X_AXIS);
        layout.split(bottomLeft, Orientation.SIDE_BY_SIDE, ViewKind.Z_AXIS);
        return layout;
    }

    /** Changes whenever the arrangement or a pane's view changes, so a user interface knows to rebuild. */
    public long getRevision() {
        return revision;
    }

    /** The panes in reading order (first children before second children). */
    public List<PaneInfo> getPanes() {
        List<PaneInfo> panes = new ArrayList<>();
        collectPanes(root, panes);
        return Collections.unmodifiableList(panes);
    }

    private static void collectPanes(Node node, List<PaneInfo> out) {
        if (node instanceof Leaf leaf) {
            out.add(new PaneInfo(leaf.id, leaf.kind));
        } else if (node instanceof Split split) {
            collectPanes(split.first, out);
            collectPanes(split.second, out);
        }
    }

    /** Number of panes. */
    public int getPaneCount() {
        return getPanes().size();
    }

    /**
     * The view of a pane.
     *
     * @throws IllegalArgumentException if there is no such pane
     */
    public ViewKind getKind(int paneId) {
        return leaf(paneId).kind;
    }

    /**
     * Changes what a pane shows.
     *
     * @throws IllegalArgumentException if there is no such pane
     */
    public void setKind(int paneId, ViewKind kind) {
        Leaf leaf = leaf(paneId);
        Objects.requireNonNull(kind, "kind cannot be null");
        if (leaf.kind != kind) {
            leaf.kind = kind;
            revision++;
        }
    }

    /**
     * Splits a pane in two. The pane keeps its id and view and becomes the first (left or top) half; the new pane
     * is the second.
     *
     * @return the id of the new pane
     * @throws IllegalArgumentException if there is no such pane
     * @throws IllegalStateException    if the layout already has {@link #MAX_PANES} panes
     */
    public int split(int paneId, Orientation orientation, ViewKind newKind) {
        Leaf target = leaf(paneId);
        Objects.requireNonNull(orientation, "orientation cannot be null");
        Objects.requireNonNull(newKind, "newKind cannot be null");
        if (getPaneCount() >= MAX_PANES) {
            throw new IllegalStateException("A layout has at most " + MAX_PANES + " panes");
        }
        Leaf created = new Leaf(nextPaneId++, newKind);
        Split parent = target.parent;
        Split split = new Split(nextSplitId++, orientation, 0.5f, target, created);
        replace(target, parent, split);
        maximizedPaneId = -1;
        revision++;
        return created.id;
    }

    /** Whether {@link #split} would succeed. */
    public boolean canSplit() {
        return getPaneCount() < MAX_PANES;
    }

    /**
     * Removes a pane; its neighbour takes over the room. The last pane cannot be closed.
     *
     * @return true if the pane was closed
     * @throws IllegalArgumentException if there is no such pane
     */
    public boolean close(int paneId) {
        Leaf target = leaf(paneId);
        Split parent = target.parent;
        if (parent == null) {
            return false;
        }
        Node sibling = parent.first == target ? parent.second : parent.first;
        replace(parent, parent.parent, sibling);
        if (maximizedPaneId == paneId) {
            maximizedPaneId = -1;
        }
        revision++;
        return true;
    }

    /** The share of its room the first child of a split gets. */
    public float getRatio(int splitId) {
        return split(splitId).ratio;
    }

    /**
     * Moves the divider of a split; the value is limited to {@link #MIN_RATIO} .. {@link #MAX_RATIO}.
     *
     * @throws IllegalArgumentException if there is no such split or the ratio is not a number
     */
    public void setRatio(int splitId, float ratio) {
        if (Float.isNaN(ratio)) {
            throw new IllegalArgumentException("ratio is not a number");
        }
        Split split = split(splitId);
        float clamped = Math.max(MIN_RATIO, Math.min(MAX_RATIO, ratio));
        if (clamped != split.ratio) {
            split.ratio = clamped;
            revision++;
        }
    }

    /** Makes one pane fill the whole area until {@link #restore()}; the layout itself is kept. */
    public void maximize(int paneId) {
        leaf(paneId);
        if (maximizedPaneId != paneId) {
            maximizedPaneId = paneId;
            revision++;
        }
    }

    /** Shows all panes again after {@link #maximize}. */
    public void restore() {
        if (maximizedPaneId != -1) {
            maximizedPaneId = -1;
            revision++;
        }
    }

    /** The maximized pane, or -1. */
    public int getMaximizedPaneId() {
        return maximizedPaneId;
    }

    /**
     * Works out where every pane and divider goes in an area of the given size, leaving {@code gap} between panes for
     * the divider. A maximized pane gets the whole area and there are no dividers. Panes are kept at least
     * {@link #MIN_PANE_SIZE} wide and high while the area has room for that.
     *
     * @throws IllegalArgumentException if the area or gap is negative or not a number
     */
    public Arrangement arrange(float width, float height, float gap) {
        if (!(width >= 0f) || !(height >= 0f) || !(gap >= 0f)) {
            throw new IllegalArgumentException("width, height and gap must be zero or more: " + width + ", " + height + ", " + gap);
        }
        List<PaneRect> panes = new ArrayList<>();
        List<Divider> dividers = new ArrayList<>();
        if (maximizedPaneId != -1) {
            Leaf leaf = leaf(maximizedPaneId);
            panes.add(new PaneRect(leaf.id, leaf.kind, 0f, 0f, width, height));
        } else {
            arrange(root, 0f, 0f, width, height, gap, panes, dividers);
        }
        return new Arrangement(Collections.unmodifiableList(panes), Collections.unmodifiableList(dividers));
    }

    private static void arrange(Node node, float x, float y, float w, float h, float gap,
                                List<PaneRect> panes, List<Divider> dividers) {
        if (node instanceof Leaf leaf) {
            panes.add(new PaneRect(leaf.id, leaf.kind, x, y, w, h));
            return;
        }
        Split split = (Split) node;
        boolean sideBySide = split.orientation == Orientation.SIDE_BY_SIDE;
        float room = Math.max(0f, (sideBySide ? w : h) - gap);
        float minShare = room > 0f ? Math.min(0.5f, MIN_PANE_SIZE / room) : 0.5f;
        float share = Math.max(minShare, Math.min(1f - minShare, split.ratio));
        float firstSize = room * share;
        float secondSize = room - firstSize;
        if (sideBySide) {
            arrange(split.first, x, y, firstSize, h, gap, panes, dividers);
            dividers.add(new Divider(split.id, split.orientation, x + firstSize, y, gap, h, x, y, w, h));
            arrange(split.second, x + firstSize + gap, y, secondSize, h, gap, panes, dividers);
        } else {
            arrange(split.first, x, y + h - firstSize, w, firstSize, gap, panes, dividers);
            dividers.add(new Divider(split.id, split.orientation, x, y + h - firstSize - gap, w, gap, x, y, w, h));
            arrange(split.second, x, y, w, secondSize, gap, panes, dividers);
        }
    }

    // --- Saving ---

    /** The layout as JSON: the tree of splits (orientation and ratio) and panes (view). A maximized pane is not saved. */
    public String toJson() {
        StringBuilder out = new StringBuilder("{\"version\":").append(JSON_VERSION).append(",\"root\":");
        writeNode(root, out);
        return out.append('}').toString();
    }

    private static void writeNode(Node node, StringBuilder out) {
        if (node instanceof Leaf leaf) {
            out.append("{\"type\":\"pane\",\"view\":\"").append(leaf.kind.name()).append("\"}");
            return;
        }
        Split split = (Split) node;
        out.append("{\"type\":\"split\",\"orientation\":\"").append(split.orientation.name())
            .append("\",\"ratio\":").append(String.format(Locale.ROOT, "%.4f", split.ratio)).append(",\"first\":");
        writeNode(split.first, out);
        out.append(",\"second\":");
        writeNode(split.second, out);
        out.append('}');
    }

    /**
     * Reads a layout written by {@link #toJson()}.
     *
     * @throws IllegalArgumentException if the text is not a valid layout, with the reason in the message
     */
    public static ViewportLayout fromJson(String json) {
        Objects.requireNonNull(json, "json cannot be null");
        JsonValue document;
        try {
            document = new JsonReader().parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The viewport layout is not valid JSON: " + e.getMessage(), e);
        }
        if (document == null || document.getInt("version", -1) != JSON_VERSION) {
            throw new IllegalArgumentException("The viewport layout has an unsupported or missing version (expected "
                + JSON_VERSION + ")");
        }
        ViewportLayout layout = new ViewportLayout(ViewKind.PERSPECTIVE);
        layout.nextPaneId = 0;
        layout.nextSplitId = 0;
        int[] paneCount = {0};
        layout.root = layout.readNode(document.get("root"), paneCount);
        return layout;
    }

    private Node readNode(JsonValue value, int[] paneCount) {
        if (value == null) {
            throw new IllegalArgumentException("The viewport layout is missing a node");
        }
        String type = value.getString("type", "");
        if (type.equals("pane")) {
            if (++paneCount[0] > MAX_PANES) {
                throw new IllegalArgumentException("The viewport layout has more than " + MAX_PANES + " panes");
            }
            return new Leaf(nextPaneId++, parseEnum(ViewKind.class, value.getString("view", ""), "view"));
        }
        if (type.equals("split")) {
            Orientation orientation = parseEnum(Orientation.class, value.getString("orientation", ""), "orientation");
            float ratio = value.getFloat("ratio", Float.NaN);
            if (Float.isNaN(ratio)) {
                throw new IllegalArgumentException("A split in the viewport layout has no ratio");
            }
            int id = nextSplitId++;
            Node first = readNode(value.get("first"), paneCount);
            Node second = readNode(value.get("second"), paneCount);
            return new Split(id, orientation, Math.max(MIN_RATIO, Math.min(MAX_RATIO, ratio)), first, second);
        }
        throw new IllegalArgumentException("The viewport layout has a node of unknown type '" + type + "'");
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, String field) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The viewport layout has an unknown " + field + " '" + name + "'", e);
        }
    }

    // --- Tree helpers ---

    private void replace(Node old, Split parent, Node replacement) {
        replacement.parent = parent;
        if (parent == null) {
            root = replacement;
        } else if (parent.first == old) {
            parent.first = replacement;
        } else {
            parent.second = replacement;
        }
    }

    private Leaf leaf(int paneId) {
        Leaf found = findLeaf(root, paneId);
        if (found == null) {
            throw new IllegalArgumentException("No pane with id " + paneId);
        }
        return found;
    }

    private static Leaf findLeaf(Node node, int paneId) {
        if (node instanceof Leaf leaf) {
            return leaf.id == paneId ? leaf : null;
        }
        Split split = (Split) node;
        Leaf inFirst = findLeaf(split.first, paneId);
        return inFirst != null ? inFirst : findLeaf(split.second, paneId);
    }

    private Split split(int splitId) {
        Split found = findSplit(root, splitId);
        if (found == null) {
            throw new IllegalArgumentException("No split with id " + splitId);
        }
        return found;
    }

    private static Split findSplit(Node node, int splitId) {
        if (node instanceof Split split) {
            if (split.id == splitId) {
                return split;
            }
            Split inFirst = findSplit(split.first, splitId);
            return inFirst != null ? inFirst : findSplit(split.second, splitId);
        }
        return null;
    }
}
