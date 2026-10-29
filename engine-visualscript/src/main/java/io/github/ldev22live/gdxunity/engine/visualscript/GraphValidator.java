package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checks a graph before it runs or is saved: known node types, wires between existing pins of matching kind and
 * type, one wire per data input and per execution output, settings filled in, and no loop among data wires
 * (a value cannot depend on itself).
 */
public final class GraphValidator {

    /** One problem; {@code nodeId} is null for problems not tied to a node. */
    public record Problem(String nodeId, String message) {}

    private final NodeRegistry registry;

    public GraphValidator(NodeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    public List<Problem> validate(Graph graph) {
        Objects.requireNonNull(graph, "graph cannot be null");
        List<Problem> problems = new ArrayList<>();
        for (GraphNode node : graph.getNodes()) {
            NodeType type = registry.get(node.getTypeId());
            if (type == null) {
                problems.add(new Problem(node.getId(), "Unknown node type " + node.getTypeId()));
                continue;
            }
            for (Map.Entry<String, ValueType> property : type.getProperties().entrySet()) {
                Object value = node.getProperty(property.getKey());
                if (!(value instanceof String s) || s.isBlank()) {
                    problems.add(new Problem(node.getId(), type.getTitle() + " needs a " + property.getKey()));
                } else if (BuiltInNodes.PROPERTY_VARIABLE.equals(property.getKey()) && graph.getVariable(s) == null) {
                    problems.add(new Problem(node.getId(), "Variable " + s + " does not exist"));
                }
            }
            for (Map.Entry<String, Object> literal : node.getLiterals().entrySet()) {
                PinDef pin = type.pin(node, graph, literal.getKey());
                if (pin != null && !pin.isExec() && pin.isInput() && !pin.type().accepts(literal.getValue())) {
                    problems.add(new Problem(node.getId(), "Value of " + pin.label() + " is not a " + pin.type()));
                }
            }
        }

        Map<String, Integer> wiresPerEnd = new HashMap<>();
        for (GraphLink link : graph.getLinks()) {
            PinDef from = pinOf(graph, link.fromNode(), link.fromPin());
            PinDef to = pinOf(graph, link.toNode(), link.toPin());
            if (from == null || to == null) {
                problems.add(new Problem(from == null ? link.fromNode() : link.toNode(), "Wire to a pin that does not exist: " + link));
                continue;
            }
            String problem = connectionProblem(from, to);
            if (problem != null) {
                problems.add(new Problem(link.toNode(), problem));
                continue;
            }
            // Single-wire ends: data inputs and execution outputs
            String end = from.isExec() ? "out:" + link.fromNode() + "." + link.fromPin() : "in:" + link.toNode() + "." + link.toPin();
            if (wiresPerEnd.merge(end, 1, Integer::sum) == 2) {
                problems.add(new Problem(from.isExec() ? link.fromNode() : link.toNode(),
                    (from.isExec() ? "Execution output " : "Input ") + (from.isExec() ? from.label() : to.label()) + " has more than one wire"));
            }
        }

        String loopNode = findDataLoop(graph);
        if (loopNode != null) {
            problems.add(new Problem(loopNode, "Data wires form a loop: a value cannot depend on itself"));
        }
        return problems;
    }

    /**
     * Why {@code from} (an output) cannot be wired to {@code to} (an input), or null when it can. Used by the
     * editor while dragging a wire.
     */
    public static String connectionProblem(PinDef from, PinDef to) {
        if (from.isInput() || !to.isInput()) {
            return "Wires go from an output to an input";
        }
        if (from.isExec() != to.isExec()) {
            return "Execution pins connect only to execution pins";
        }
        if (!from.isExec() && from.type() != to.type()) {
            return "Cannot connect " + from.type() + " to " + to.type();
        }
        return null;
    }

    private PinDef pinOf(Graph graph, String nodeId, String pinId) {
        GraphNode node = graph.getNode(nodeId);
        NodeType type = node != null ? registry.get(node.getTypeId()) : null;
        return type != null ? type.pin(node, graph, pinId) : null;
    }

    /** A node on a cycle of data wires, or null. */
    private String findDataLoop(Graph graph) {
        Map<String, List<String>> dependsOn = new HashMap<>();
        for (GraphLink link : graph.getLinks()) {
            PinDef from = pinOf(graph, link.fromNode(), link.fromPin());
            if (from != null && !from.isExec()) {
                dependsOn.computeIfAbsent(link.toNode(), k -> new ArrayList<>()).add(link.fromNode());
            }
        }
        Set<String> done = new HashSet<>();
        for (String start : dependsOn.keySet()) {
            String loop = visit(start, dependsOn, new HashSet<>(), done);
            if (loop != null) {
                return loop;
            }
        }
        return null;
    }

    private static String visit(String node, Map<String, List<String>> dependsOn, Set<String> onPath, Set<String> done) {
        if (done.contains(node)) {
            return null;
        }
        if (!onPath.add(node)) {
            return node;
        }
        for (String next : dependsOn.getOrDefault(node, List.of())) {
            String loop = visit(next, dependsOn, onPath, done);
            if (loop != null) {
                return loop;
            }
        }
        onPath.remove(node);
        done.add(node);
        return null;
    }
}
