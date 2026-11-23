package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A visual script: nodes, the wires between their pins, and variables (TD-029). Saved as a {@code .graph} asset
 * and run per entity by {@link GraphInstance}. The graph itself does no validation; see {@link GraphValidator}.
 */
public final class Graph {
    private String name;
    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<GraphLink> links = new ArrayList<>();
    private final Map<String, GraphVariable> variables = new LinkedHashMap<>();
    private final Map<String, GraphFunction> functions = new LinkedHashMap<>();
    private final Map<String, GraphEventDef> events = new LinkedHashMap<>();
    /** The function this graph is the body of (TD-070), or null for a graph of its own. */
    private GraphFunction owner;
    /** For a function body: the graph the function belongs to, whose variables, functions and events the body uses. */
    private Graph parent;
    private int nextNodeNumber = 1;

    public Graph(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = (name == null || name.isBlank()) ? "Graph" : name;
    }

    /** A node id not used in this graph yet ({@code n1}, {@code n2}, ...). */
    public String newNodeId() {
        String id;
        do {
            id = "n" + nextNodeNumber++;
        } while (nodes.containsKey(id));
        return id;
    }

    public void addNode(GraphNode node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (nodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("Duplicate node id: " + node.getId());
        }
        nodes.put(node.getId(), node);
    }

    /** Removes the node; its wires are not touched (callers remove them first). */
    public GraphNode removeNode(String id) {
        return nodes.remove(id);
    }

    public GraphNode getNode(String id) {
        return nodes.get(id);
    }

    public List<GraphNode> getNodes() {
        return List.copyOf(nodes.values());
    }

    public void addLink(GraphLink link) {
        Objects.requireNonNull(link, "link cannot be null");
        if (!links.contains(link)) {
            links.add(link);
        }
    }

    public boolean removeLink(GraphLink link) {
        return links.remove(link);
    }

    public List<GraphLink> getLinks() {
        return Collections.unmodifiableList(new ArrayList<>(links));
    }

    /** Wires ending at the given input pin. */
    public List<GraphLink> linksInto(String nodeId, String pinId) {
        List<GraphLink> result = new ArrayList<>();
        for (GraphLink link : links) {
            if (link.toNode().equals(nodeId) && link.toPin().equals(pinId)) {
                result.add(link);
            }
        }
        return result;
    }

    /** Wires leaving the given output pin. */
    public List<GraphLink> linksFrom(String nodeId, String pinId) {
        List<GraphLink> result = new ArrayList<>();
        for (GraphLink link : links) {
            if (link.fromNode().equals(nodeId) && link.fromPin().equals(pinId)) {
                result.add(link);
            }
        }
        return result;
    }

    public List<GraphLink> linksTouching(String nodeId) {
        List<GraphLink> result = new ArrayList<>();
        for (GraphLink link : links) {
            if (link.touches(nodeId)) {
                result.add(link);
            }
        }
        return result;
    }

    public void putVariable(GraphVariable variable) {
        Objects.requireNonNull(variable, "variable cannot be null");
        variables.put(variable.name(), variable);
    }

    public GraphVariable removeVariable(String name) {
        return variables.remove(name);
    }

    /** A variable of this graph; in a function body, of the graph the function belongs to. */
    public GraphVariable getVariable(String name) {
        if (name == null) {
            return null;
        }
        GraphVariable own = variables.get(name);
        return own == null && parent != null ? parent.getVariable(name) : own;
    }

    public List<GraphVariable> getVariables() {
        return List.copyOf(variables.values());
    }

    // --- Functions and custom events (TD-070) ---

    /** The function whose body this graph is, or null when it is a graph of its own. */
    public GraphFunction getOwner() {
        return owner;
    }

    void setOwner(GraphFunction owner) {
        this.owner = owner;
    }

    /** @throws IllegalArgumentException when a function of that name exists, or this graph is itself a function body */
    public void putFunction(GraphFunction function) {
        Objects.requireNonNull(function, "function cannot be null");
        if (owner != null) {
            throw new IllegalArgumentException("A function body cannot hold functions");
        }
        if (functions.containsKey(function.getName())) {
            throw new IllegalArgumentException("Function " + function.getName() + " already exists");
        }
        functions.put(function.getName(), function);
        function.getBody().parent = this;
    }

    public GraphFunction removeFunction(String name) {
        GraphFunction removed = functions.remove(name);
        if (removed != null) {
            removed.getBody().parent = null;
        }
        return removed;
    }

    /** A function of this graph; in a function body, of the graph the function belongs to. */
    public GraphFunction getFunction(String name) {
        if (name == null) {
            return null;
        }
        GraphFunction own = functions.get(name);
        return own == null && parent != null ? parent.getFunction(name) : own;
    }

    /** The graph a function body belongs to, or null. */
    public Graph getParent() {
        return parent;
    }

    public List<GraphFunction> getFunctions() {
        return List.copyOf(functions.values());
    }

    /** @throws IllegalArgumentException when an event of that name is declared, or this graph is a function body */
    public void putEvent(GraphEventDef event) {
        Objects.requireNonNull(event, "event cannot be null");
        if (owner != null) {
            throw new IllegalArgumentException("A function body cannot declare events");
        }
        if (events.containsKey(event.getName())) {
            throw new IllegalArgumentException("Event " + event.getName() + " already exists");
        }
        events.put(event.getName(), event);
    }

    public GraphEventDef removeEvent(String name) {
        return events.remove(name);
    }

    /** An event declared by this graph; in a function body, by the graph the function belongs to. */
    public GraphEventDef getEvent(String name) {
        if (name == null) {
            return null;
        }
        GraphEventDef own = events.get(name);
        return own == null && parent != null ? parent.getEvent(name) : own;
    }

    public List<GraphEventDef> getEvents() {
        return List.copyOf(events.values());
    }

    /** Whether the graph needs {@link GraphSerializer#SCHEMA_VERSION_FUNCTIONS} to be saved. */
    public boolean usesFunctionsOrEvents() {
        return !functions.isEmpty() || !events.isEmpty();
    }
}
