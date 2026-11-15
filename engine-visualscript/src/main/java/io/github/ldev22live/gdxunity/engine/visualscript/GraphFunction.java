package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A function of a graph (TD-070): a name, typed inputs and outputs, and a body graph of its own that starts at one Function Entry
 * node and hands its outputs back at Return nodes. A Function Call node in another graph (or function) runs the body to its end
 * and then continues; the body shares the variables of the graph it belongs to. Bodies hold no events and no delays.
 */
public final class GraphFunction {
    private final String name;
    private final List<GraphParam> inputs = new ArrayList<>();
    private final List<GraphParam> outputs = new ArrayList<>();
    private final Graph body;

    public GraphFunction(String name) {
        if (!GraphParam.isValidName(name)) {
            throw new IllegalArgumentException("Not a usable function name: '" + name + "'");
        }
        this.name = name;
        this.body = new Graph(name);
        this.body.setOwner(this);
    }

    public String getName() {
        return name;
    }

    public List<GraphParam> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    public List<GraphParam> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    /** The graph the function runs. */
    public Graph getBody() {
        return body;
    }

    /** @throws IllegalArgumentException when an input or output of that name exists */
    public void addInput(GraphParam param) {
        requireFree(param.name());
        inputs.add(param);
    }

    /** @throws IllegalArgumentException when an input or output of that name exists */
    public void addOutput(GraphParam param) {
        requireFree(param.name());
        outputs.add(param);
    }

    /** Puts {@code param} back at {@code index} among the inputs (undoing a removal keeps the pin order). */
    public void insertInput(int index, GraphParam param) {
        requireFree(param.name());
        inputs.add(index, param);
    }

    /** Puts {@code param} back at {@code index} among the outputs (undoing a removal keeps the pin order). */
    public void insertOutput(int index, GraphParam param) {
        requireFree(param.name());
        outputs.add(index, param);
    }

    /** @return whether there was an input of that name */
    public boolean removeInput(String paramName) {
        return inputs.removeIf(p -> p.name().equals(paramName));
    }

    /** @return whether there was an output of that name */
    public boolean removeOutput(String paramName) {
        return outputs.removeIf(p -> p.name().equals(paramName));
    }

    private void requireFree(String paramName) {
        Objects.requireNonNull(paramName, "paramName cannot be null");
        if (inputs.stream().anyMatch(p -> p.name().equals(paramName)) || outputs.stream().anyMatch(p -> p.name().equals(paramName))) {
            throw new IllegalArgumentException("Function " + name + " already has a parameter named " + paramName);
        }
    }
}
