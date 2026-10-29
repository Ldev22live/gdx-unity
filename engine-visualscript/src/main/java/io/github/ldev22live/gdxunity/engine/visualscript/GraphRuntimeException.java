package io.github.ldev22live.gdxunity.engine.visualscript;

/** A node failed while a graph was running; {@link #getNodeId()} lets the editor point at it. */
public class GraphRuntimeException extends RuntimeException {
    private final String nodeId;

    public GraphRuntimeException(String nodeId, String message) {
        super(message);
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
