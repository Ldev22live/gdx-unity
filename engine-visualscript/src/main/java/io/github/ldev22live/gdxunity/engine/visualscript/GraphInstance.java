package io.github.ldev22live.gdxunity.engine.visualscript;

import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One graph running for one entity: an interpreter (TD-029). Events start execution at their event nodes; action
 * nodes run in order along execution wires; pure nodes are computed when an input reads them, so their values are
 * always current. Outputs of action and event nodes keep the value from their last run.
 * <p>
 * A failing node stops only the chain it is on and is reported to the {@link GraphHost}; the graph keeps handling
 * later events. A chain that runs more than {@link #MAX_NODES_PER_EVENT} nodes is stopped as a probable endless loop.
 */
public final class GraphInstance {
    /** Nodes one event may run before it is stopped as an endless loop. */
    public static final int MAX_NODES_PER_EVENT = 10_000;
    /** Deepest chain of pure nodes one input may read through. */
    public static final int MAX_DATA_DEPTH = 256;

    private final Graph graph;
    private final NodeRegistry registry;
    private final Entity self;
    private final GraphHost host;
    private final Map<String, Object> variables = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> lastOutputs = new HashMap<>();
    private final List<PendingDelay> delays = new ArrayList<>();
    private boolean stopped;

    private static final class PendingDelay {
        final String nodeId;
        final String pinId;
        float remaining;

        PendingDelay(String nodeId, String pinId, float remaining) {
            this.nodeId = nodeId;
            this.pinId = pinId;
            this.remaining = remaining;
        }
    }

    /**
     * @throws IllegalArgumentException when the graph has validation problems (the first one is in the message)
     */
    public GraphInstance(Graph graph, NodeRegistry registry, Entity self, GraphHost host) {
        this.graph = Objects.requireNonNull(graph, "graph cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.self = Objects.requireNonNull(self, "self cannot be null");
        this.host = Objects.requireNonNull(host, "host cannot be null");
        List<GraphValidator.Problem> problems = new GraphValidator(registry).validate(graph);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Graph " + graph.getName() + " has " + problems.size()
                + " problem(s), first: " + problems.get(0).message());
        }
        for (GraphVariable variable : graph.getVariables()) {
            variables.put(variable.name(), variable.type().copy(variable.initialValue()));
        }
    }

    public Graph getGraph() {
        return graph;
    }

    public Entity getSelf() {
        return self;
    }

    // --- Events ---

    public void beginPlay() {
        fire(BuiltInNodes.EVENT_BEGIN_PLAY, Map.of(), null);
    }

    /** Finishes delays that ran out, then fires Event Tick. */
    public void tick(float deltaSeconds) {
        if (!isRunning()) {
            return;
        }
        List<PendingDelay> due = new ArrayList<>();
        for (Iterator<PendingDelay> it = delays.iterator(); it.hasNext(); ) {
            PendingDelay delay = it.next();
            delay.remaining -= deltaSeconds;
            if (delay.remaining <= 0f) {
                due.add(delay);
                it.remove();
            }
        }
        for (PendingDelay delay : due) {
            runChain(delay.nodeId, delay.pinId);
        }
        fire(BuiltInNodes.EVENT_TICK, Map.of(BuiltInNodes.PIN_DELTA_TIME, deltaSeconds), null);
    }

    public void collision(Entity other) {
        fire(BuiltInNodes.EVENT_COLLISION, payloadWithOther(other), null);
    }

    public void triggerEnter(Entity other) {
        fire(BuiltInNodes.EVENT_TRIGGER, payloadWithOther(other), null);
    }

    /** Runs the Custom Event nodes named {@code name}; returns false when the graph has none. */
    public boolean fireCustomEvent(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        return fire(BuiltInNodes.EVENT_CUSTOM, Map.of(), name);
    }

    /** No more events run; pending delays are dropped. */
    public void stop() {
        stopped = true;
        delays.clear();
    }

    /** Running until stopped or until its entity leaves the scene. */
    public boolean isRunning() {
        return !stopped && self.getScene() != null;
    }

    private static Map<String, Object> payloadWithOther(Entity other) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(BuiltInNodes.PIN_OTHER, other);
        return payload;
    }

    private boolean fire(String eventTypeId, Map<String, Object> payload, String customName) {
        if (!isRunning()) {
            return false;
        }
        boolean handled = false;
        for (GraphNode node : graph.getNodes()) {
            if (!node.getTypeId().equals(eventTypeId)
                || (customName != null && !customName.equals(node.getProperty(BuiltInNodes.PROPERTY_NAME)))) {
                continue;
            }
            handled = true;
            Map<String, Object> outputs = lastOutputs.computeIfAbsent(node.getId(), k -> new HashMap<>());
            outputs.putAll(payload);
            host.nodeExecuted(this, node.getId());
            runChain(node.getId(), PinDef.EXEC_OUT);
        }
        return handled;
    }

    // --- Variables ---

    public Object getVariable(String name) {
        GraphVariable variable = graph.getVariable(name);
        if (variable == null) {
            throw new IllegalArgumentException("Unknown variable: " + name);
        }
        return variable.type().copy(variables.get(name));
    }

    public void setVariable(String name, Object value) {
        GraphVariable variable = graph.getVariable(name);
        if (variable == null) {
            throw new IllegalArgumentException("Unknown variable: " + name);
        }
        if (!variable.type().accepts(value) || value == null) {
            throw new IllegalArgumentException("Variable " + name + " holds " + variable.type() + ", not " + value);
        }
        variables.put(name, variable.type().copy(value));
    }

    // --- Execution ---

    /** Runs everything wired after output {@code pinId} of node {@code nodeId}. */
    private void runChain(String nodeId, String pinId) {
        Deque<String[]> pending = new ArrayDeque<>();
        pending.push(new String[] {nodeId, pinId});
        int count = 0;
        String current = nodeId;
        try {
            while (!pending.isEmpty() && isRunning()) {
                String[] next = pending.pop();
                for (GraphLink link : graph.linksFrom(next[0], next[1])) {
                    GraphNode target = graph.getNode(link.toNode());
                    current = target.getId();
                    if (++count > MAX_NODES_PER_EVENT) {
                        throw new GraphRuntimeException(current,
                            "Stopped after " + MAX_NODES_PER_EVENT + " nodes in one event; is there an endless loop?");
                    }
                    Context context = new Context(target, 0);
                    host.nodeExecuted(this, target.getId());
                    registry.get(target.getTypeId()).run(context);
                    // Continue in the order the node asked for (a stack pops the last push first)
                    for (int i = context.continuations.size() - 1; i >= 0; i--) {
                        pending.push(new String[] {target.getId(), context.continuations.get(i)});
                    }
                }
            }
        } catch (GraphRuntimeException e) {
            host.error(this, e);
        } catch (RuntimeException e) {
            host.error(this, new GraphRuntimeException(current, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /** Value of output {@code pinId} of node {@code nodeId}, computing pure nodes now. */
    private Object valueOf(String nodeId, String pinId, int depth) {
        GraphNode source = graph.getNode(nodeId);
        NodeType type = registry.get(source.getTypeId());
        PinDef pin = type.pin(source, graph, pinId);
        if (type.getKind() == NodeType.Kind.PURE) {
            if (depth > MAX_DATA_DEPTH) {
                throw new GraphRuntimeException(nodeId, "Data chain deeper than " + MAX_DATA_DEPTH + " nodes");
            }
            Context context = new Context(source, depth);
            type.run(context);
            Object value = context.pureOutputs.get(pinId);
            return value != null || pin.type() == ValueType.ENTITY ? value : pin.type().defaultValue();
        }
        Map<String, Object> outputs = lastOutputs.get(nodeId);
        Object value = outputs != null ? outputs.get(pinId) : null;
        return value != null || pin.type() == ValueType.ENTITY ? value : pin.type().defaultValue();
    }

    private final class Context implements NodeContext {
        final GraphNode node;
        final NodeType type;
        final int depth;
        final List<String> continuations = new ArrayList<>();
        final Map<String, Object> pureOutputs = new HashMap<>();

        Context(GraphNode node, int depth) {
            this.node = node;
            this.type = registry.get(node.getTypeId());
            this.depth = depth;
        }

        @Override
        public Object input(String pinId) {
            PinDef pin = type.pin(node, graph, pinId);
            if (pin == null || pin.isExec() || !pin.isInput()) {
                throw error("No data input " + pinId);
            }
            List<GraphLink> wires = graph.linksInto(node.getId(), pinId);
            if (!wires.isEmpty()) {
                GraphLink wire = wires.get(0);
                return valueOf(wire.fromNode(), wire.fromPin(), depth + 1);
            }
            Object literal = node.getLiteral(pinId);
            return literal != null ? pin.type().copy(literal) : pin.type().defaultValue();
        }

        @Override
        public Entity inputEntity(String pinId) {
            if (graph.linksInto(node.getId(), pinId).isEmpty()) {
                return self;
            }
            return (Entity) input(pinId);
        }

        @Override
        public void output(String pinId, Object value) {
            if (type.getKind() == NodeType.Kind.PURE) {
                pureOutputs.put(pinId, value);
            } else {
                lastOutputs.computeIfAbsent(node.getId(), k -> new HashMap<>()).put(pinId, value);
            }
        }

        @Override
        public Object property(String name) {
            return node.getProperty(name);
        }

        @Override
        public void continueWith(String pinId) {
            requireExecOutput(pinId);
            continuations.add(pinId);
        }

        @Override
        public void continueAfter(float seconds, String pinId) {
            requireExecOutput(pinId);
            for (PendingDelay delay : delays) {
                if (delay.nodeId.equals(node.getId())) {
                    return; // already waiting: a running Delay ignores new calls
                }
            }
            delays.add(new PendingDelay(node.getId(), pinId, seconds));
        }

        private void requireExecOutput(String pinId) {
            PinDef pin = type.pin(node, graph, pinId);
            if (pin == null || !pin.isExec() || pin.isInput()) {
                throw error("No execution output " + pinId);
            }
        }

        @Override
        public Entity self() {
            return self;
        }

        @Override
        public GraphInstance instance() {
            return GraphInstance.this;
        }

        @Override
        public void log(String message) {
            host.log(GraphInstance.this, message);
        }

        @Override
        public GraphRuntimeException error(String message) {
            return new GraphRuntimeException(node.getId(), message);
        }
    }
}
