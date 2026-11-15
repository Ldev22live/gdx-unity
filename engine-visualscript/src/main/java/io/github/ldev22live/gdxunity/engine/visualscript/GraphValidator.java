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
                } else if (FunctionNodes.PROPERTY_FUNCTION.equals(property.getKey()) && graph.getFunction(s) == null) {
                    problems.add(new Problem(node.getId(), "Function " + s + " does not exist"));
                } else if (FunctionNodes.FIRE.equals(node.getTypeId()) && BuiltInNodes.PROPERTY_NAME.equals(property.getKey())
                    && graph.getEvent(s) == null) {
                    problems.add(new Problem(node.getId(), "Custom event " + s + " is not declared; declare it with its parameters first"));
                }
            }
            problems.addAll(placementProblems(graph, node, type));
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
        if (graph.getOwner() != null) {
            long entries = graph.getNodes().stream().filter(n -> FunctionNodes.ENTRY.equals(n.getTypeId())).count();
            if (entries != 1) {
                problems.add(new Problem(null, "Function " + graph.getOwner().getName() + " needs exactly one Function Entry node, has " + entries));
            }
        } else {
            for (GraphFunction function : graph.getFunctions()) {
                for (Problem inner : validate(function.getBody())) {
                    problems.add(new Problem(null, "Function " + function.getName() + ": " + inner.message()));
                }
            }
            String cycle = findCallCycle(graph);
            if (cycle != null) {
                problems.add(new Problem(null, "Functions call each other in a circle (" + cycle + "); a function cannot call itself, directly or through others"));
            }
        }
        return problems;
    }

    /** Nodes that only make sense in a function body, or only outside one, and what a body may not hold. */
    private List<Problem> placementProblems(Graph graph, GraphNode node, NodeType type) {
        List<Problem> problems = new ArrayList<>();
        boolean inBody = graph.getOwner() != null;
        String id = node.getTypeId();
        if (!inBody && (FunctionNodes.ENTRY.equals(id) || FunctionNodes.RETURN.equals(id))) {
            problems.add(new Problem(node.getId(), type.getTitle() + " can only be used inside a function"));
        }
        if (inBody && type.getKind() == NodeType.Kind.EVENT && !FunctionNodes.ENTRY.equals(id)) {
            problems.add(new Problem(node.getId(), "A function cannot hold " + type.getTitle() + "; events belong to the graph"));
        }
        if (inBody && BuiltInNodes.FLOW_DELAY.equals(id)) {
            problems.add(new Problem(node.getId(), "A function cannot hold a Delay: it runs to its end in one go"));
        }
        return problems;
    }

    /** A path like {@code a -> b -> a} when functions call each other in a circle, else null. */
    private static String findCallCycle(Graph graph) {
        Map<String, List<String>> calls = new HashMap<>();
        for (GraphFunction function : graph.getFunctions()) {
            List<String> called = new ArrayList<>();
            for (GraphNode node : function.getBody().getNodes()) {
                if (FunctionNodes.CALL.equals(node.getTypeId()) && node.getProperty(FunctionNodes.PROPERTY_FUNCTION) instanceof String name) {
                    called.add(name);
                }
            }
            calls.put(function.getName(), called);
        }
        Set<String> done = new HashSet<>();
        for (String start : calls.keySet()) {
            String path = callPath(start, calls, new java.util.ArrayDeque<>(), done);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private static String callPath(String name, Map<String, List<String>> calls, java.util.Deque<String> path, Set<String> done) {
        if (done.contains(name) || !calls.containsKey(name)) {
            return null;
        }
        if (path.contains(name)) {
            List<String> loop = new ArrayList<>(path);
            java.util.Collections.reverse(loop);
            loop.add(name);
            return String.join(" -> ", loop.subList(loop.indexOf(name), loop.size()));
        }
        path.push(name);
        for (String next : calls.get(name)) {
            String found = callPath(next, calls, path, done);
            if (found != null) {
                return found;
            }
        }
        path.pop();
        done.add(name);
        return null;
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
        if (!from.isExec() && !accepts(to, from)) {
            return "Cannot connect " + describe(from) + " to " + describe(to);
        }
        return null;
    }

    /**
     * Whether a value from {@code from} can go into {@code to}: the target's class takes the source's class (boxed values and
     * entities included), and a whole number may go where a float is wanted. A float never goes where an integer is wanted: say
     * so with a node.
     */
    public static boolean accepts(PinDef to, PinDef from) {
        if (to.type() == ValueType.FLOAT && from.type() == ValueType.INTEGER) {
            return true;
        }
        return to.valueClass().isAssignableFrom(from.valueClass());
    }

    private static String describe(PinDef pin) {
        return pin.type() == ValueType.OBJECT ? pin.objectClass().getSimpleName() : pin.type().toString();
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
