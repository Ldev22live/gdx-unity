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

    public GraphVariable getVariable(String name) {
        return name != null ? variables.get(name) : null;
    }

    public List<GraphVariable> getVariables() {
        return List.copyOf(variables.values());
    }
}
