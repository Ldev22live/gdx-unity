package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Nodes of graph functions and parameterized custom events (TD-070), shared by desktop and web graphs: Function Entry and Return (only in
 * a function body), Function Call, Fire Custom Event, and the pins a declared Custom Event node gets from its parameters.
 */
public final class FunctionNodes {
    /** First node of a function body; its data outputs are the function's inputs. */
    public static final String ENTRY = "function.entry";
    /** Hands the function's outputs back and ends the call; a body may have several. */
    public static final String RETURN = "function.return";
    /** Runs a function of the graph, then continues; its data outputs are the function's outputs. */
    public static final String CALL = "function.call";
    /** Runs the Custom Event nodes of a declared event, with the event's parameters, then continues. */
    public static final String FIRE = "event.fire";
    /** Property of a Function Call node: the function's name. */
    public static final String PROPERTY_FUNCTION = "function";
    /** Function calls nested deeper than this are stopped (validation already refuses recursion, so only a very long chain reaches it). */
    public static final int MAX_CALL_DEPTH = 32;
    /** Custom events firing each other deeper than this are stopped as an endless loop. */
    public static final int MAX_EVENT_DEPTH = 32;

    private FunctionNodes() { }

    /** Pins of a Custom Event node: the execution output, plus one output per parameter of the declared event of that name. */
    static List<PinDef> customEventPins(GraphNode node, Graph graph) {
        List<PinDef> pins = new ArrayList<>();
        pins.add(PinDef.execOut(PinDef.EXEC_OUT, ""));
        GraphEventDef event = graph.getEvent(nameOf(node));
        if (event != null) {
            event.getParams().forEach(p -> pins.add(p.asOutputPin()));
        }
        return pins;
    }

    static void registerAll(NodeRegistry registry) {
        Objects.requireNonNull(registry, "registry cannot be null");
        registry.register(NodeType.builder(ENTRY, "Function Entry", "Functions", NodeType.Kind.EVENT)
            .dynamicPins((node, graph) -> {
                List<PinDef> pins = new ArrayList<>();
                pins.add(PinDef.execOut(PinDef.EXEC_OUT, ""));
                if (graph.getOwner() != null) {
                    graph.getOwner().getInputs().forEach(p -> pins.add(p.asOutputPin()));
                }
                return pins;
            }).build());
        registry.register(NodeType.builder(RETURN, "Return", "Functions", NodeType.Kind.ACTION)
            .execOutputs()
            .dynamicPins((node, graph) -> {
                List<PinDef> pins = new ArrayList<>();
                pins.add(PinDef.execIn());
                if (graph.getOwner() != null) {
                    graph.getOwner().getOutputs().forEach(p -> pins.add(p.asInputPin()));
                }
                return pins;
            })
            .behaviour(c -> {
                Map<String, Object> values = new LinkedHashMap<>();
                GraphFunction function = c.instance().getGraph().getOwner();
                if (function == null) {
                    throw c.error("Return can only be used inside a function");
                }
                for (GraphParam output : function.getOutputs()) {
                    values.put(output.name(), c.input(output.name()));
                }
                c.instance().returnFromFunction(values);
            }).build());
        registry.register(NodeType.builder(CALL, "Call Function", "Functions", NodeType.Kind.ACTION)
            .property(PROPERTY_FUNCTION, ValueType.STRING)
            .dynamicPins((node, graph) -> {
                List<PinDef> pins = new ArrayList<>();
                pins.add(PinDef.execIn());
                pins.add(PinDef.execOut(PinDef.EXEC_OUT, ""));
                GraphFunction function = graph.getFunction((String) node.getProperty(PROPERTY_FUNCTION));
                if (function != null) {
                    function.getInputs().forEach(p -> pins.add(p.asInputPin()));
                    function.getOutputs().forEach(p -> pins.add(p.asOutputPin()));
                }
                return pins;
            })
            .behaviour(c -> {
                String name = (String) c.property(PROPERTY_FUNCTION);
                GraphFunction function = c.instance().getGraph().getFunction(name);
                if (function == null) {
                    throw c.error("Function " + name + " does not exist");
                }
                Map<String, Object> arguments = new LinkedHashMap<>();
                for (GraphParam input : function.getInputs()) {
                    arguments.put(input.name(), c.input(input.name()));
                }
                Map<String, Object> results = c.instance().callFunction(function, arguments);
                for (GraphParam output : function.getOutputs()) {
                    c.output(output.name(), results.get(output.name()));
                }
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
        registry.register(NodeType.builder(FIRE, "Fire Custom Event", "Events", NodeType.Kind.ACTION)
            .property(BuiltInNodes.PROPERTY_NAME, ValueType.STRING)
            .dynamicPins((node, graph) -> {
                List<PinDef> pins = new ArrayList<>();
                pins.add(PinDef.execIn());
                pins.add(PinDef.execOut(PinDef.EXEC_OUT, ""));
                GraphEventDef event = graph.getEvent(nameOf(node));
                if (event != null) {
                    event.getParams().forEach(p -> pins.add(p.asInputPin()));
                }
                return pins;
            })
            .behaviour(c -> {
                String name = (String) c.property(BuiltInNodes.PROPERTY_NAME);
                GraphEventDef event = c.instance().getGraph().getEvent(name);
                if (event == null) {
                    throw c.error("Custom event " + name + " is not declared");
                }
                Map<String, Object> arguments = new LinkedHashMap<>();
                for (GraphParam param : event.getParams()) {
                    arguments.put(param.name(), c.input(param.name()));
                }
                c.instance().fireEvent(name, arguments);
                c.continueWith(PinDef.EXEC_OUT);
            }).build());
    }

    /**
     * Source generation does not cover functions and parameterized events yet (TD-070): a graph that has them runs in the interpreter
     * on the desktop and on the web, but cannot become Java, Kotlin or TypeScript.
     *
     * @throws IllegalArgumentException saying so, when {@code graph} declares functions or events or fires a custom event
     */
    static void refuseForGeneration(Graph graph, String target) {
        boolean fires = graph.getNodes().stream().anyMatch(n -> FIRE.equals(n.getTypeId()) || CALL.equals(n.getTypeId()));
        if (graph.usesFunctionsOrEvents() || fires) {
            throw new IllegalArgumentException("Graph " + graph.getName() + " uses functions or declared custom events: " + target
                + " source generation does not support them yet; the graph runs as it is in the editor, the game runtime and the web player");
        }
    }

    private static String nameOf(GraphNode node) {
        Object name = node.getProperty(BuiltInNodes.PROPERTY_NAME);
        return name instanceof String s ? s : null;
    }
}
