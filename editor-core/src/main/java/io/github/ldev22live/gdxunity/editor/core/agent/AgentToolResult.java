package io.github.ldev22live.gdxunity.editor.core.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured outcome returned by an AgentTool upon execution.
 */
public class AgentToolResult {
    private final boolean success;
    private final String message;
    private final Map<String, Object> outputs;
    private final Throwable error;

    private AgentToolResult(boolean success, String message, Map<String, Object> outputs, Throwable error) {
        this.success = success;
        this.message = (message != null) ? message : "";
        this.outputs = (outputs != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(outputs)) : Collections.emptyMap();
        this.error = error;
    }

    public static AgentToolResult success(String message) {
        return new AgentToolResult(true, message, Collections.emptyMap(), null);
    }

    public static AgentToolResult success(String message, Map<String, Object> outputs) {
        return new AgentToolResult(true, message, outputs, null);
    }

    public static AgentToolResult failure(String message) {
        return new AgentToolResult(false, message, Collections.emptyMap(), null);
    }

    public static AgentToolResult failure(String message, Throwable error) {
        return new AgentToolResult(false, message, Collections.emptyMap(), error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }

    public Object getOutput(String key) {
        return outputs.get(key);
    }

    public Throwable getError() {
        return error;
    }
}
