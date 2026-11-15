package io.github.ldev22live.gdxunity.editor.visualscript;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.visualscript.FunctionNodes;
import io.github.ldev22live.gdxunity.engine.visualscript.Graph;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphEventDef;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphFunction;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphLink;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphNode;
import io.github.ldev22live.gdxunity.engine.visualscript.GraphParam;
import io.github.ldev22live.gdxunity.engine.visualscript.PinDef;
import io.github.ldev22live.gdxunity.engine.visualscript.BuiltInNodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Undoable edits of a graph's functions and declared custom events (TD-070): add and remove a function or event, add and remove
 * their inputs, outputs and parameters. Removing a parameter also removes the wires on its pin, and undoing puts both back.
 * Each factory returns one {@link EditorCommand} to run with {@link GraphDocument#execute}.
 */
public final class GraphSignatures {
    /** Where a new function's Return node goes, right of its Function Entry node. */
    private static final float RETURN_OFFSET_X = 320f;

    private GraphSignatures() { }

    /** One change with its inverse. */
    private static final class Mutation implements EditorCommand {
        private final String description;
        private final Runnable apply;
        private final Runnable revert;

        Mutation(String description, Runnable apply, Runnable revert) {
            this.description = description;
            this.apply = apply;
            this.revert = revert;
        }

        @Override
        public boolean execute() {
            apply.run();
            return true;
        }

        @Override
        public boolean undo() {
            revert.run();
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    /**
     * A new function with a Function Entry and a Return node wired together, so it is valid at once.
     *
     * @throws IllegalArgumentException when the name is unusable or taken
     */
    public static EditorCommand addFunction(Graph main, String name) {
        Objects.requireNonNull(main, "main cannot be null");
        if (main.getOwner() != null) {
            throw new IllegalArgumentException("Functions live in the graph, not in another function");
        }
        if (main.getFunction(name) != null) {
            throw new IllegalArgumentException("Function " + name + " already exists");
        }
        GraphFunction function = new GraphFunction(name);
        Graph body = function.getBody();
        GraphNode entry = new GraphNode(body.newNodeId(), FunctionNodes.ENTRY, 0f, 0f);
        body.addNode(entry);
        GraphNode ret = new GraphNode(body.newNodeId(), FunctionNodes.RETURN, RETURN_OFFSET_X, 0f);
        body.addNode(ret);
        body.addLink(new GraphLink(entry.getId(), PinDef.EXEC_OUT, ret.getId(), PinDef.EXEC_IN));
        return new Mutation("Add Function " + name, () -> main.putFunction(function), () -> main.removeFunction(name));
    }

    /** Removes the function; calls of it stay in the graph and are reported as problems. */
    public static EditorCommand removeFunction(Graph main, String name) {
        GraphFunction function = main.getFunction(name);
        if (function == null) {
            throw new IllegalArgumentException("No function " + name);
        }
        return new Mutation("Remove Function " + name, () -> main.removeFunction(name), () -> main.putFunction(function));
    }

    public static EditorCommand addInput(Graph main, String function, GraphParam param) {
        GraphFunction f = require(main, function);
        return new Mutation("Add Input " + param.name(), () -> f.addInput(param), () -> f.removeInput(param.name()));
    }

    public static EditorCommand addOutput(Graph main, String function, GraphParam param) {
        GraphFunction f = require(main, function);
        return new Mutation("Add Output " + param.name(), () -> f.addOutput(param), () -> f.removeOutput(param.name()));
    }

    /** Removes the input and the wires on its pin (the entry node's output and every call's input). */
    public static EditorCommand removeInput(Graph main, String function, String name) {
        GraphFunction f = require(main, function);
        GraphParam param = find(f.getInputs(), name);
        int index = f.getInputs().indexOf(param);
        List<Wire> wires = wiresOf(main, f, node -> node.getTypeId().equals(FunctionNodes.ENTRY), true, name);
        return new Mutation("Remove Input " + name, () -> {
            wires.forEach(Wire::remove);
            f.removeInput(name);
        }, () -> {
            f.insertInput(index, param);
            wires.forEach(Wire::restore);
        });
    }

    /** Removes the output and the wires on its pin (every Return node's input and every call's output). */
    public static EditorCommand removeOutput(Graph main, String function, String name) {
        GraphFunction f = require(main, function);
        GraphParam param = find(f.getOutputs(), name);
        int index = f.getOutputs().indexOf(param);
        List<Wire> wires = wiresOf(main, f, node -> node.getTypeId().equals(FunctionNodes.RETURN), false, name);
        return new Mutation("Remove Output " + name, () -> {
            wires.forEach(Wire::remove);
            f.removeOutput(name);
        }, () -> {
            f.insertOutput(index, param);
            wires.forEach(Wire::restore);
        });
    }

    /** @throws IllegalArgumentException when the name is unusable or taken */
    public static EditorCommand addEvent(Graph main, String name) {
        Objects.requireNonNull(main, "main cannot be null");
        if (main.getEvent(name) != null) {
            throw new IllegalArgumentException("Event " + name + " already exists");
        }
        GraphEventDef event = new GraphEventDef(name);
        return new Mutation("Add Event " + name, () -> main.putEvent(event), () -> main.removeEvent(name));
    }

    /** Removes the declaration; Custom Event and Fire nodes of that name stay and are reported (Fire) or lose their parameters (Custom Event). */
    public static EditorCommand removeEvent(Graph main, String name) {
        GraphEventDef event = main.getEvent(name);
        if (event == null) {
            throw new IllegalArgumentException("No event " + name);
        }
        List<Wire> wires = new ArrayList<>();
        for (GraphParam param : event.getParams()) {
            wires.addAll(eventWires(main, name, param.name()));
        }
        return new Mutation("Remove Event " + name, () -> {
            wires.forEach(Wire::remove);
            main.removeEvent(name);
        }, () -> {
            main.putEvent(event);
            wires.forEach(Wire::restore);
        });
    }

    public static EditorCommand addEventParam(Graph main, String event, GraphParam param) {
        GraphEventDef e = requireEvent(main, event);
        return new Mutation("Add Event Parameter " + param.name(), () -> e.addParam(param), () -> e.removeParam(param.name()));
    }

    /** Removes the parameter and the wires on its pin (Custom Event outputs, Fire inputs). */
    public static EditorCommand removeEventParam(Graph main, String event, String name) {
        GraphEventDef e = requireEvent(main, event);
        GraphParam param = find(e.getParams(), name);
        int index = e.getParams().indexOf(param);
        List<Wire> wires = eventWires(main, event, name);
        return new Mutation("Remove Event Parameter " + name, () -> {
            wires.forEach(Wire::remove);
            e.removeParam(name);
        }, () -> {
            e.insertParam(index, param);
            wires.forEach(Wire::restore);
        });
    }

    // --- Helpers ---

    private static GraphFunction require(Graph main, String name) {
        GraphFunction function = main.getFunction(name);
        if (function == null) {
            throw new IllegalArgumentException("No function " + name);
        }
        return function;
    }

    private static GraphEventDef requireEvent(Graph main, String name) {
        GraphEventDef event = main.getEvent(name);
        if (event == null) {
            throw new IllegalArgumentException("No event " + name);
        }
        return event;
    }

    private static GraphParam find(List<GraphParam> params, String name) {
        return params.stream().filter(p -> p.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No parameter " + name));
    }

    /** A wire taken out of a graph, with the graph to put it back in. */
    private record Wire(Graph graph, GraphLink link) {
        void remove() {
            graph.removeLink(link);
        }

        void restore() {
            graph.addLink(link);
        }
    }

    /**
     * The wires on the pin called {@code name} of a function: on its body's entry (outputs of an input) or return nodes (inputs of an
     * output), and on every call of the function in the graph and in all bodies.
     */
    private static List<Wire> wiresOf(Graph main, GraphFunction function, Predicate<GraphNode> bodyNode, boolean entrySide, String name) {
        List<Wire> wires = new ArrayList<>();
        Graph body = function.getBody();
        for (GraphLink link : body.getLinks()) {
            GraphNode from = body.getNode(link.fromNode());
            GraphNode to = body.getNode(link.toNode());
            if (entrySide && from != null && bodyNode.test(from) && link.fromPin().equals(name)
                || !entrySide && to != null && bodyNode.test(to) && link.toPin().equals(name)) {
                wires.add(new Wire(body, link));
            }
        }
        List<Graph> graphs = new ArrayList<>();
        graphs.add(main);
        main.getFunctions().forEach(f -> graphs.add(f.getBody()));
        for (Graph graph : graphs) {
            for (GraphLink link : graph.getLinks()) {
                GraphNode from = graph.getNode(link.fromNode());
                GraphNode to = graph.getNode(link.toNode());
                boolean callOutput = !entrySide && isCallOf(from, function) && link.fromPin().equals(name);
                boolean callInput = entrySide && isCallOf(to, function) && link.toPin().equals(name);
                if (callOutput || callInput) {
                    wires.add(new Wire(graph, link));
                }
            }
        }
        return wires;
    }

    private static List<Wire> eventWires(Graph main, String event, String name) {
        List<Wire> wires = new ArrayList<>();
        for (GraphLink link : main.getLinks()) {
            GraphNode from = main.getNode(link.fromNode());
            GraphNode to = main.getNode(link.toNode());
            boolean receiverOutput = from != null && from.getTypeId().equals(BuiltInNodes.EVENT_CUSTOM)
                && event.equals(from.getProperty(BuiltInNodes.PROPERTY_NAME)) && link.fromPin().equals(name);
            boolean fireInput = to != null && to.getTypeId().equals(FunctionNodes.FIRE)
                && event.equals(to.getProperty(BuiltInNodes.PROPERTY_NAME)) && link.toPin().equals(name);
            if (receiverOutput || fireInput) {
                wires.add(new Wire(main, link));
            }
        }
        return wires;
    }

    private static boolean isCallOf(GraphNode node, GraphFunction function) {
        return node != null && node.getTypeId().equals(FunctionNodes.CALL)
            && function.getName().equals(node.getProperty(FunctionNodes.PROPERTY_FUNCTION));
    }
}
