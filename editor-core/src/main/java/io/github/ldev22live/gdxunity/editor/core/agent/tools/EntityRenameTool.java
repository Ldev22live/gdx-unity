package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.command.RenameEntityCommand;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.*;

/**
 * Tool to rename an existing entity in the active scene.
 */
public class EntityRenameTool implements AgentTool {
    public static final String ID = "entity_rename";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Renames an existing entity in the active scene.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
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

        if (parameters == null || !parameters.containsKey("name") || parameters.get("name") == null || parameters.get("name").toString().isBlank()) {
            errors.add("Missing or blank required parameter 'name'");
        } else if (parameters.get("name").toString().length() > 256) {
            errors.add("Parameter 'name' exceeds maximum length of 256 characters");
        }

        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        UUID id = parseUUID(parameters.get("entityId"));
        String newName = parameters.get("name").toString();
        Scene scene = context.getActiveScene();
        Entity entity = (scene != null) ? scene.findEntityById(id) : null;
        String oldName = (entity != null) ? entity.getName() : "";
        return new RenameEntityCommand(context.getEditorState(), id, oldName, newName);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        EditorCommand cmd = createCommand(parameters, context);
        boolean success = cmd.execute();
        if (!success) {
            return AgentToolResult.failure("Failed to rename entity");
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("entityId", parameters.get("entityId").toString());
        outputs.put("name", parameters.get("name").toString());
        return AgentToolResult.success("Renamed entity to '" + parameters.get("name") + "'", outputs);
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
