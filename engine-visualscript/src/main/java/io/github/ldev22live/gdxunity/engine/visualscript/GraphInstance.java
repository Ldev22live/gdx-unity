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
    /** Shared by the graph's own instance and every function frame it starts. */
    private final Map<String, Object> variables;
    private final Map<String, Map<String, Object>> lastOutputs = new HashMap<>();
    private final List<PendingDelay> delays = new ArrayList<>();
    private boolean stopped;
    /** For the frame of a function call (TD-070): the instance that made the call; null for the graph's own instance. */
    private final GraphInstance caller;
    /** How many function calls deep this frame is; 0 for the graph's own instance. */
    private final int callDepth;
    /** Custom events firing custom events, how deep now (the graph's own instance only). */
    private int eventDepth;
    /** Set by a Return node: the function frame's outputs; the frame stops running. */
    private Map<String, Object> returned;

    private record Continuation(String nodeId, String pinId, Integer index) { }

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
    /** Whether the key with this libGDX key code is held down, according to the host. */
    public boolean isKeyDown(int keycode) {
        return host.isKeyDown(keycode);
    }

    /** Whether the key with this libGDX key code went down since the last frame, according to the host. */
    public boolean isKeyJustPressed(int keycode) {
        return host.isKeyJustPressed(keycode);
    }

    public GraphInstance(Graph graph, NodeRegistry registry, Entity self, GraphHost host) {
        this.graph = Objects.requireNonNull(graph, "graph cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.self = Objects.requireNonNull(self, "self cannot be null");
        this.host = Objects.requireNonNull(host, "host cannot be null");
        this.caller = null;
        this.callDepth = 0;
        this.variables = new LinkedHashMap<>();
        List<GraphValidator.Problem> problems = new GraphValidator(registry).validate(graph);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Graph " + graph.getName() + " has " + problems.size()
                + " problem(s), first: " + problems.get(0).message());
        }
        for (GraphVariable variable : graph.getVariables()) {
            variables.put(variable.name(), variable.type().copy(variable.initialValue()));
        }
    }

    /** The frame of one call of {@code function}: its body graph, with the variables, host and entity of the caller. */
    private GraphInstance(GraphInstance caller, GraphFunction function) {
        this.graph = function.getBody();
        this.registry = caller.registry;
        this.self = caller.self;
        this.host = caller.host;
        this.caller = caller;
        this.callDepth = caller.callDepth + 1;
        this.variables = caller.variables;
    }

    public Graph getGraph() {
        return graph;
    }

    /** The graph's own instance: this one, or for a function frame the instance that started the outermost call. */
    private GraphInstance root() {
        return caller == null ? this : caller.root();
    }

    // --- Functions (TD-070) ---

    /**
     * Runs {@code function} to its Return (or to the end of its chain) and gives back its outputs by name; an output no Return reached
     * holds the default of its type. Errors in the body stop the caller's chain too.
     *
     * @param arguments the function's inputs by name
     * @throws GraphRuntimeException when the body fails or the calls are nested deeper than {@link FunctionNodes#MAX_CALL_DEPTH}
     */
    public Map<String, Object> callFunction(GraphFunction function, Map<String, Object> arguments) {
        Objects.requireNonNull(function, "function cannot be null");
        Objects.requireNonNull(arguments, "arguments cannot be null");
        if (callDepth >= FunctionNodes.MAX_CALL_DEPTH) {
            throw new GraphRuntimeException(null, "Functions called more than " + FunctionNodes.MAX_CALL_DEPTH + " deep: " + function.getName());
        }
        GraphInstance frame = new GraphInstance(this, function);
        GraphNode entry = null;
        for (GraphNode node : function.getBody().getNodes()) {
            if (FunctionNodes.ENTRY.equals(node.getTypeId())) {
                entry = node;
            }
        }
        if (entry == null) {
            throw new GraphRuntimeException(null, "Function " + function.getName() + " has no Function Entry node");
        }
        frame.lastOutputs.put(entry.getId(), new HashMap<>(arguments));
        frame.runChain(entry.getId(), PinDef.EXEC_OUT);
        Map<String, Object> results = new LinkedHashMap<>();
        for (GraphParam output : function.getOutputs()) {
            Object value = frame.returned != null ? frame.returned.get(output.name()) : null;
            results.put(output.name(), value != null || output.type().allowsNull() ? value : output.type().defaultValue());
        }
        return results;
    }

    /** Called by a Return node: records the outputs and ends this function frame. */
    void returnFromFunction(Map<String, Object> values) {
        if (caller == null) {
            throw new IllegalStateException("Return outside a function");
        }
        returned = values;
        stopped = true;
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

    /**
     * Runs the Custom Event nodes named {@code name} with the event's parameters in {@code arguments} (by name), before this call
     * returns; used by Fire Custom Event nodes (TD-070).
     *
     * @throws GraphRuntimeException when events fire each other deeper than {@link FunctionNodes#MAX_EVENT_DEPTH}
     */
    public boolean fireEvent(String name, Map<String, Object> arguments) {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(arguments, "arguments cannot be null");
        GraphInstance root = root();
        if (root.eventDepth >= FunctionNodes.MAX_EVENT_DEPTH) {
            throw new GraphRuntimeException(null, "Custom events fired more than " + FunctionNodes.MAX_EVENT_DEPTH
                + " deep: " + name + "; does an event fire itself?");
        }
        root.eventDepth++;
        try {
            return root.fire(BuiltInNodes.EVENT_CUSTOM, arguments, name);
        } finally {
            root.eventDepth--;
        }
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
        Deque<Continuation> pending = new ArrayDeque<>();
        pending.push(new Continuation(nodeId, pinId, null));
        int count = 0;
        String current = nodeId;
        try {
            while (!pending.isEmpty() && isRunning()) {
                Continuation next = pending.pop();
                if (next.index() != null) {
                    lastOutputs.computeIfAbsent(next.nodeId(), k -> new HashMap<>()).put("index", next.index());
                }
                for (GraphLink link : graph.linksFrom(next.nodeId(), next.pinId())) {
                    GraphNode target = graph.getNode(link.toNode());
                    current = target.getId();
                    if (++count > MAX_NODES_PER_EVENT) {
                        throw new GraphRuntimeException(current,
                            "Stopped after " + MAX_NODES_PER_EVENT + " nodes in one event; is there an endless loop?");
                    }
                    Context context = new Context(target, 0);
                    if (caller == null) {
                        host.nodeExecuted(this, target.getId());
                    }
                    registry.get(target.getTypeId()).run(context);
                    // Continue in the order the node asked for (a stack pops the last push first)
                    for (int i = context.continuations.size() - 1; i >= 0; i--) {
                        pending.push(context.continuations.get(i));
                    }
                }
            }
        } catch (GraphRuntimeException e) {
            reportOrRethrow(e);
        } catch (RuntimeException e) {
            reportOrRethrow(new GraphRuntimeException(current, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    /** The graph's own instance reports an error to the host; a function frame hands it to its caller, so the calling chain stops. */
    private void reportOrRethrow(GraphRuntimeException error) {
        if (caller != null) {
            throw error;
        }
        host.error(this, error);
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
            return value != null || pin.type().allowsNull() ? value : pin.type().defaultValue();
        }
        Map<String, Object> outputs = lastOutputs.get(nodeId);
        Object value = outputs != null ? outputs.get(pinId) : null;
        return value != null || pin.type().allowsNull() ? value : pin.type().defaultValue();
    }

    private final class Context implements NodeContext {
        final GraphNode node;
        final NodeType type;
        final int depth;
        final List<Continuation> continuations = new ArrayList<>();
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
                Object value = valueOf(wire.fromNode(), wire.fromPin(), depth + 1);
                // A whole number may be wired into a float pin (GraphValidator.accepts): hand the node the float it asked for
                return pin.type() == ValueType.FLOAT && value instanceof Integer whole ? (Object) whole.floatValue() : value;
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
            continuations.add(new Continuation(node.getId(), pinId, null));
        }

        @Override
        public void repeat(int count) {
            if (!LoopNodes.REPEAT.equals(node.getTypeId()) || count < 0 || count > LoopNodes.MAX_ITERATIONS) {
                throw error("Invalid Repeat count: " + count);
            }
            output("index", 0);
            for (int index = 0; index < count; index++) {
                continuations.add(new Continuation(node.getId(), "body", index));
            }
            continuations.add(new Continuation(node.getId(), "completed", null));
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
