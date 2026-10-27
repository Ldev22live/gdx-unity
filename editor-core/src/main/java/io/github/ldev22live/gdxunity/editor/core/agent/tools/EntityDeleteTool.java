package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;

import java.util.*;

/**
 * Tool to delete an entity from the active scene while preserving undo capability.
 */
public class EntityDeleteTool implements AgentTool {
    public static final String ID = "entity_delete";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Deletes an entity from the active scene with full hierarchy and component preservation.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (parameters == null || !parameters.containsKey("entityId")) {
            errors.add("Missing required parameter 'entityId'");
        } else {
            UUID id = parseUUID(parameters.get("entityId"));
            if (id == null) {
                errors.add("Invalid UUID format for 'entityId'");
            } else if (context.getActiveScene() != null && context.getActiveScene().findEntityById(id) == null) {
                errors.add("Entity with ID '" + id + "' not found in active scene");
            }
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        UUID id = parseUUID(parameters.get("entityId"));
        return new DeleteEntityCommand(context.getEditorState(), id);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        EditorCommand cmd = createCommand(parameters, context);
        boolean success = cmd.execute();
        if (!success) {
            return AgentToolResult.failure("Failed to delete entity");
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("deletedEntityId", parameters.get("entityId").toString());
        return AgentToolResult.success("Deleted entity '" + parameters.get("entityId") + "'", outputs);
    }

    private static UUID parseUUID(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID) return (UUID) obj;
        try {
            return UUID.fromString(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
