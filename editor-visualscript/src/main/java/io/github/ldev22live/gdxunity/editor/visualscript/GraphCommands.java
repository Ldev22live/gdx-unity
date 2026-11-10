package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphValidator;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphVariable;
import io.github.ldev22live.gdxunity.engine.visualscript.JavaMethodNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeRegistry;
import io.github.ldev22live.gdxunity.engine.visualscript.NodeType;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Undoable edits of a {@link Graph}; each graph document has its own history (TD-029). */
public final class GraphCommands {

    private GraphCommands() {}

    public static final class AddNode implements EditorCommand {
        private final Graph graph;
        private final GraphNode node;

        public AddNode(Graph graph, GraphNode node) {
            this.graph = Objects.requireNonNull(graph, "graph cannot be null");
            this.node = Objects.requireNonNull(node, "node cannot be null");
        }

        @Override
        public boolean execute() {
            graph.addNode(node);
            return true;
        }

        @Override
        public boolean undo() {
            return graph.removeNode(node.getId()) != null;
        }

        @Override
        public String getDescription() {
            return "Add Node";
        }
    }

    /** Removes nodes together with every wire touching them. */
    public static final class RemoveNodes implements EditorCommand {
        private final Graph graph;
        private final List<String> nodeIds;
        private final List<GraphNode> removedNodes = new ArrayList<>();
        private final List<GraphLink> removedLinks = new ArrayList<>();

        public RemoveNodes(Graph graph, List<String> nodeIds) {
            this.graph = Objects.requireNonNull(graph, "graph cannot be null");
            this.nodeIds = List.copyOf(nodeIds);
        }

        @Override
        public boolean execute() {
            removedNodes.clear();
            removedLinks.clear();
            for (String id : nodeIds) {
                for (GraphLink link : graph.linksTouching(id)) {
                    if (!removedLinks.contains(link)) {
                        removedLinks.add(link);
                        graph.removeLink(link);
                    }
                }
                GraphNode node = graph.removeNode(id);
                if (node != null) {
                    removedNodes.add(node);
                }
            }
            return !removedNodes.isEmpty();
        }

        @Override
        public boolean undo() {
            removedNodes.forEach(graph::addNode);
            removedLinks.forEach(graph::addLink);
            return true;
        }

        @Override
        public String getDescription() {
            return nodeIds.size() == 1 ? "Delete Node" : "Delete " + nodeIds.size() + " Nodes";
        }
    }

    public static final class MoveNode implements EditorCommand {
        private final GraphNode node;
        private final float fromX;
        private final float fromY;
        private final float toX;
        private final float toY;

        public MoveNode(GraphNode node, float fromX, float fromY, float toX, float toY) {
            this.node = Objects.requireNonNull(node, "node cannot be null");
            this.fromX = fromX;
            this.fromY = fromY;
            this.toX = toX;
            this.toY = toY;
        }

        @Override
        public boolean execute() {
            node.setPosition(toX, toY);
            return fromX != toX || fromY != toY;
        }

        @Override
        public boolean undo() {
            node.setPosition(fromX, fromY);
            return true;
        }

        @Override
        public String getDescription() {
            return "Move Node";
        }
    }

    /**
     * Wires an output to an input. A data input and an execution output hold one wire, so an existing wire there
     * is replaced. Refused (returns false) when the pins are incompatible or on the same node.
     */
    public static final class Connect implements EditorCommand {
        private final Graph graph;
        private final NodeRegistry registry;
        private final GraphLink link;
        private final List<GraphLink> replaced = new ArrayList<>();
        private String refusal;

        public Connect(Graph graph, NodeRegistry registry, GraphLink link) {
            this.graph = Objects.requireNonNull(graph, "graph cannot be null");
            this.registry = Objects.requireNonNull(registry, "registry cannot be null");
            this.link = Objects.requireNonNull(link, "link cannot be null");
        }

        /** Why the last execution was refused, or null. */
        public String getRefusal() {
            return refusal;
        }

        @Override
        public boolean execute() {
            PinDef from = pin(graph, registry, link.fromNode(), link.fromPin());
            PinDef to = pin(graph, registry, link.toNode(), link.toPin());
            if (from == null || to == null) {
                refusal = "Pin not found";
                return false;
            }
            if (link.fromNode().equals(link.toNode())) {
                refusal = "A node cannot be wired to itself";
                return false;
            }
            refusal = GraphValidator.connectionProblem(from, to);
            if (refusal != null || graph.getLinks().contains(link)) {
                return false;
            }
            replaced.clear();
            replaced.addAll(from.isExec() ? graph.linksFrom(link.fromNode(), link.fromPin()) : graph.linksInto(link.toNode(), link.toPin()));
            replaced.forEach(graph::removeLink);
            graph.addLink(link);
            return true;
        }

        @Override
        public boolean undo() {
            graph.removeLink(link);
            replaced.forEach(graph::addLink);
            return true;
        }

        @Override
        public String getDescription() {
            return "Connect Pins";
        }
    }

    public static final class Disconnect implements EditorCommand {
        private final Graph graph;
        private final List<GraphLink> links;

        public Disconnect(Graph graph, List<GraphLink> links) {
            this.graph = Objects.requireNonNull(graph, "graph cannot be null");
            this.links = List.copyOf(links);
        }

        @Override
        public boolean execute() {
            boolean any = false;
            for (GraphLink link : links) {
                any |= graph.removeLink(link);
            }
            return any;
        }

        @Override
        public boolean undo() {
            links.forEach(graph::addLink);
            return true;
        }

        @Override
        public String getDescription() {
            return "Break Link";
        }
    }

    /** Sets the literal typed into an unconnected data input (null clears it). */
    public static final class SetLiteral implements EditorCommand {
        private final GraphNode node;
        private final String pinId;
        private final Object oldValue;
        private final Object newValue;

        public SetLiteral(GraphNode node, String pinId, Object newValue) {
            this.node = Objects.requireNonNull(node, "node cannot be null");
            this.pinId = Objects.requireNonNull(pinId, "pinId cannot be null");
            this.oldValue = node.getLiteral(pinId);
            this.newValue = newValue;
        }

        @Override
        public boolean execute() {
            node.setLiteral(pinId, newValue);
            return !Objects.equals(oldValue, newValue);
        }

        @Override
        public boolean undo() {
            node.setLiteral(pinId, oldValue);
            return true;
        }

        @Override
        public String getDescription() {
            return "Change Value";
        }
    }

    /**
     * Changes a node setting. For variable nodes this changes their pins, so wires to pins that no longer exist or
     * changed type are removed (and restored on undo).
     */
    public static final class SetProperty implements EditorCommand {
        private final Graph graph;
        private final NodeRegistry registry;
        private final GraphNode node;
        private final String name;
        private final Object oldValue;
        private final Object newValue;
        private final List<GraphLink> dropped = new ArrayList<>();

        public SetProperty(Graph graph, NodeRegistry registry, GraphNode node, String name, Object newValue) {
            this.graph = Objects.requireNonNull(graph, "graph cannot be null");
            this.registry = Objects.requireNonNull(registry, "registry cannot be null");
            this.node = Objects.requireNonNull(node, "node cannot be null");
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.oldValue = node.getProperty(name);
            this.newValue = newValue;
        }

        @Override
        public boolean execute() {
            node.setProperty(name, newValue);
            dropped.clear();
            for (GraphLink link : graph.linksTouching(node.getId())) {
                PinDef from = pin(graph, registry, link.fromNode(), link.fromPin());
                PinDef to = pin(graph, registry, link.toNode(), link.toPin());
                if (from == null || to == null || GraphValidator.connectionProblem(from, to) != null) {
                    dropped.add(link);
                    graph.removeLink(link);
                }
            }
            return !Objects.equals(oldValue, newValue);
        }

        @Override
        public boolean undo() {
            node.setProperty(name, oldValue);
            dropped.forEach(graph::addLink);
            return true;
        }

        @Override
        public String getDescription() {
            return "Change " + name;
        }
    }

    /**
     * Switches a method node between Pure and Action (TD-060): the same method, either read whenever a pin needs its result or run once
     * in the chain of execution. The wires to its data pins stay; switching to Pure drops the wires to its execution pins, and undo
     * brings them back.
     */
    public static final class SwitchMethodKind implements EditorCommand {
        private final Graph graph;
        private final NodeRegistry registry;
        private final String nodeId;
        private GraphNode before;
        private final List<GraphLink> droppedLinks = new ArrayList<>();

        public SwitchMethodKind(Graph graph, NodeRegistry registry, String nodeId) {
            this.graph = Objects.requireNonNull(graph, "graph cannot be null");
            this.registry = Objects.requireNonNull(registry, "registry cannot be null");
            this.nodeId = Objects.requireNonNull(nodeId, "nodeId cannot be null");
        }

        /** Whether the node can be switched: it is a method node with a twin. */
        public static boolean canSwitch(Graph graph, NodeRegistry registry, String nodeId) {
            GraphNode node = graph.getNode(nodeId);
            return node != null && JavaMethodNodes.counterpartOf(node.getTypeId(), registry) != null;
        }

        @Override
        public boolean execute() {
            before = graph.getNode(nodeId);
            if (before == null) {
                return false;
            }
            String other = JavaMethodNodes.counterpartOf(before.getTypeId(), registry);
            if (other == null) {
                return false;
            }
            GraphNode after = new GraphNode(nodeId, other, before.getX(), before.getY());
            before.getLiterals().forEach(after::setLiteral);
            before.getProperties().forEach(after::setProperty);
            droppedLinks.clear();
            if (!JavaMethodNodes.isActionTwin(other)) {
                for (GraphLink link : graph.linksTouching(nodeId)) {
                    boolean execIn = link.toNode().equals(nodeId) && link.toPin().equals(PinDef.EXEC_IN);
                    boolean execOut = link.fromNode().equals(nodeId) && link.fromPin().equals(PinDef.EXEC_OUT);
                    if (execIn || execOut) {
                        droppedLinks.add(link);
                        graph.removeLink(link);
                    }
                }
            }
            graph.removeNode(nodeId);
            graph.addNode(after);
            return true;
        }

        @Override
        public boolean undo() {
            graph.removeNode(nodeId);
            graph.addNode(before);
            droppedLinks.forEach(graph::addLink);
            return true;
        }

        @Override
        public String getDescription() {
            return "Switch Node Between Pure and Action";
        }
    }

    public static final class AddVariable implements EditorCommand {
        private final Graph graph;
        private final GraphVariable variable;

        public AddVariable(Graph graph, GraphVariable variable) {
            this.graph = Objects.requireNonNull(graph, "graph cannot be null");
            this.variable = Objects.requireNonNull(variable, "variable cannot be null");
        }

        @Override
        public boolean execute() {
            if (graph.getVariable(variable.name()) != null) {
                return false;
            }
            graph.putVariable(variable);
            return true;
        }

        @Override
        public boolean undo() {
            return graph.removeVariable(variable.name()) != null;
        }

        @Override
        public String getDescription() {
            return "Add Variable " + variable.name();
        }
    }

    static PinDef pin(Graph graph, NodeRegistry registry, String nodeId, String pinId) {
        GraphNode node = graph.getNode(nodeId);
        NodeType type = node != null ? registry.get(node.getTypeId()) : null;
        return type != null ? type.pin(node, graph, pinId) : null;
    }
}
