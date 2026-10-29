package io.github.ldev22live.gdxunity.engine.visualscript;

import java.util.Objects;

/** A wire from an output pin to an input pin. */
public record GraphLink(String fromNode, String fromPin, String toNode, String toPin) {
    public GraphLink {
        Objects.requireNonNull(fromNode, "fromNode cannot be null");
        Objects.requireNonNull(fromPin, "fromPin cannot be null");
        Objects.requireNonNull(toNode, "toNode cannot be null");
        Objects.requireNonNull(toPin, "toPin cannot be null");
    }

    public boolean touches(String nodeId) {
        return fromNode.equals(nodeId) || toNode.equals(nodeId);
    }
}
