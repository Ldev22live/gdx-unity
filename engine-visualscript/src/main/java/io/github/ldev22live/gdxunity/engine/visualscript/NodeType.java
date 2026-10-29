package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A kind of node offered in the graph editor, and what it does when it runs.
 * <ul>
 *   <li>{@link Kind#EVENT}: starts execution when its event happens; its data outputs carry the event's values.</li>
 *   <li>{@link Kind#ACTION}: runs when execution reaches its input, then continues through an output.</li>
 *   <li>{@link Kind#PURE}: no execution pins; computes its outputs whenever a connected node reads them.</li>
 * </ul>
 */
public final class NodeType {

    public enum Kind { EVENT, ACTION, PURE }

    private final String id;
    private final String title;
    private final String category;
    private final Kind kind;
    private final List<PinDef> pins;
    private final Map<String, ValueType> properties;
    private final Consumer<NodeContext> behaviour;
    private final BiFunction<GraphNode, Graph, List<PinDef>> dynamicPins;

    private NodeType(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.category = builder.category;
        this.kind = builder.kind;
        this.pins = List.copyOf(builder.pins);
        this.properties = Map.copyOf(builder.properties);
        this.behaviour = builder.behaviour;
        this.dynamicPins = builder.dynamicPins;
    }

    public static Builder builder(String id, String title, String category, Kind kind) {
        return new Builder(id, title, category, kind);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public Kind getKind() {
        return kind;
    }

    /** Settings stored on each node (not pins), such as a variable or custom event name. */
    public Map<String, ValueType> getProperties() {
        return properties;
    }

    /** Pins of {@code node}; most types have fixed pins, variable nodes take theirs from the variable's type. */
    public List<PinDef> pinsFor(GraphNode node, Graph graph) {
        return dynamicPins != null ? dynamicPins.apply(node, graph) : pins;
    }

    public PinDef pin(GraphNode node, Graph graph, String pinId) {
        for (PinDef pin : pinsFor(node, graph)) {
            if (pin.id().equals(pinId)) {
                return pin;
            }
        }
        return null;
    }

    /** Runs an action, or computes a pure node's outputs. Events have no behaviour of their own. */
    void run(NodeContext context) {
        if (behaviour != null) {
            behaviour.accept(context);
        }
    }

    public static final class Builder {
        private final String id;
        private final String title;
        private final String category;
        private final Kind kind;
        private final List<PinDef> pins = new ArrayList<>();
        private final Map<String, ValueType> properties = new LinkedHashMap<>();
        private Consumer<NodeContext> behaviour;
        private BiFunction<GraphNode, Graph, List<PinDef>> dynamicPins;

        private Builder(String id, String title, String category, Kind kind) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.title = Objects.requireNonNull(title, "title cannot be null");
            this.category = Objects.requireNonNull(category, "category cannot be null");
            this.kind = Objects.requireNonNull(kind, "kind cannot be null");
            if (kind == Kind.ACTION) {
                pins.add(PinDef.execIn());
                pins.add(PinDef.execOut(PinDef.EXEC_OUT, ""));
            } else if (kind == Kind.EVENT) {
                pins.add(PinDef.execOut(PinDef.EXEC_OUT, ""));
            }
        }

        public Builder pin(PinDef pin) {
            pins.add(pin);
            return this;
        }

        /** Replaces the default single execution output (Branch, Sequence). */
        public Builder execOutputs(PinDef... outputs) {
            pins.removeIf(p -> p.isExec() && !p.isInput());
            pins.addAll(0, List.of(outputs));
            return this;
        }

        public Builder property(String name, ValueType type) {
            properties.put(name, type);
            return this;
        }

        public Builder behaviour(Consumer<NodeContext> behaviour) {
            this.behaviour = behaviour;
            return this;
        }

        public Builder dynamicPins(BiFunction<GraphNode, Graph, List<PinDef>> dynamicPins) {
            this.dynamicPins = dynamicPins;
            return this;
        }

        public NodeType build() {
            if (kind != Kind.EVENT && behaviour == null) {
                throw new IllegalStateException("Node type " + id + " needs a behaviour");
            }
            return new NodeType(this);
        }
    }
}
