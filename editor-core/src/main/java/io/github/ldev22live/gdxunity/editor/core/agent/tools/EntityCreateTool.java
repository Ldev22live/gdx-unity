package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.*;

/**
 * Tool to create a new entity in the active scene.
 */
public class EntityCreateTool implements AgentTool {
    public static final String ID = "entity_create";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Creates a new entity in the active scene with an optional initial name.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (context.getActiveScene() == null) {
            errors.add("No active scene in editor");
        }
        if (parameters != null && parameters.containsKey("name")) {
            Object nameObj = parameters.get("name");
            if (nameObj != null && nameObj.toString().length() > 256) {
                errors.add("Entity name exceeds maximum length of 256 characters");
            }
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        String name = "New Entity";
        if (parameters != null && parameters.get("name") != null) {
            name = parameters.get("name").toString();
        }
        return new CreateEntityCommand(context.getEditorState(), name);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        EditorCommand cmd = createCommand(parameters, context);
        boolean success = cmd.execute();
        if (!success) {
            return AgentToolResult.failure("Failed to create entity");
        }

        CreateEntityCommand createCmd = (CreateEntityCommand) cmd;
        Entity created = createCmd.getCreatedEntity();

        Map<String, Object> outputs = new LinkedHashMap<>();
        if (created != null) {
            outputs.put("entityId", created.getId().toString());
            outputs.put("name", created.getName());
        }

        return AgentToolResult.success("Created entity '" + (created != null ? created.getName() : "") + "'", outputs);
    }
}
