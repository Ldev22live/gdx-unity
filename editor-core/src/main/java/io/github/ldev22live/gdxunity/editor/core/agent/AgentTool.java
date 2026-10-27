package io.github.ldev22live.gdxunity.editor.core.agent;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fundamental contract for an AI agent tool.
 * Represents an operation over editor/scene state with schema validation and command execution.
 */
public interface AgentTool {
    /**
     * Unique, stable identifier for this tool (e.g. "scene_inspect", "entity_create").
     */
    String getId();

    /**
     * Human-readable description of what the tool accomplishes.
     */
    String getDescription();

    /**
     * Whether this tool only inspects state without changing scene or project data.
     */
    boolean isReadOnly();

    /**
     * Whether executing this tool constitutes a destructive operation requiring
     * explicit user confirmation or elevated permission before execution.
     * Defaults to false.
     */
    default boolean requiresConfirmation() {
        return false;
    }

    /**
     * Validates that the supplied parameters conform to the tool's required schema and value ranges.
     *
     * @param parameters parameter key-value pairs (untrusted input)
     * @param context    active execution context
     * @return null or empty list if valid; list of error messages if invalid
     */
    List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context);

    /**
     * Creates the concrete EditorCommand to execute for this tool invocation.
     * Returns null if the tool is read-only or does not record command history.
     */
    EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) throws Exception;

    /**
     * Executes the tool logic and returns structured output data.
     * For mutating tools, this is invoked in tandem with command execution.
     */
    AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception;
}
