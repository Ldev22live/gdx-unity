package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A custom event declared by a graph (TD-070): a name and typed parameters. Custom Event nodes of that name get one data output per
 * parameter, and Fire Custom Event nodes one data input per parameter. A Custom Event node whose name is not declared keeps
 * working as before, with no parameters.
 */
public final class GraphEventDef {
    private final String name;
    private final List<GraphParam> params = new ArrayList<>();

    public GraphEventDef(String name) {
        if (!GraphParam.isValidName(name)) {
            throw new IllegalArgumentException("Not a usable event name: '" + name + "'");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<GraphParam> getParams() {
        return Collections.unmodifiableList(params);
    }

    /** @throws IllegalArgumentException when a parameter of that name exists */
    public void addParam(GraphParam param) {
        Objects.requireNonNull(param, "param cannot be null");
        if (params.stream().anyMatch(p -> p.name().equals(param.name()))) {
            throw new IllegalArgumentException("Event " + name + " already has a parameter named " + param.name());
        }
        params.add(param);
    }

    /** Puts {@code param} back at {@code index} (undoing a removal keeps the pin order). */
    public void insertParam(int index, GraphParam param) {
        Objects.requireNonNull(param, "param cannot be null");
        if (params.stream().anyMatch(p -> p.name().equals(param.name()))) {
            throw new IllegalArgumentException("Event " + name + " already has a parameter named " + param.name());
        }
        params.add(index, param);
    }

    /** @return whether there was a parameter of that name */
    public boolean removeParam(String paramName) {
        return params.removeIf(p -> p.name().equals(paramName));
    }
}
