package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.workspace.ModelingHandOff;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Says what the 3D Modeling workspace can do for an entity (roadmap M3c.4): the same facts as the capability notice a person sees after
 * Customize > Edit 3D Model ({@link ModelingHandOff}). Reports source availability independently of instance transforms.
 *
 * <p>Parameter: {@code entityId} (optional; the selected entity when left out). Outputs: {@code entityId} (empty for no selection),
 * {@code name}, {@code capability} ("sculpt", "mesh-source", "mesh-planned", "no-mesh" or "no-selection"), {@code meshEditing}, {@code sculptCopy},
 * {@code materialSlot}, {@code model} (the glTF path, or empty), {@code title} and {@code notice} (the notice's sentences).
 */
public class ModelEditCheckTool implements AgentTool {
    public static final String ID = "model_edit_check";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Says what 3D Modeling can do for an entity: whether its mesh (vertices, edges, faces) can be edited, whether a sculpt copy "
            + "can be made, and whether its material can be changed. Parameter entityId (optional; the selected entity otherwise). "
            + "Outputs capability (sculpt, mesh-source, mesh-planned, no-mesh or no-selection), meshEditing, sculptCopy, materialSlot, model, title "
            + "and notice. Supported static sources can be edited in the modeling preview.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (context.getActiveScene() == null) {
            errors.add("No scene is open");
            return errors;
        }
        if (parameters != null && parameters.get("entityId") != null) {
            UUID id = parseId(parameters.get("entityId"));
            if (id == null) {
                errors.add("Invalid UUID format for 'entityId': " + parameters.get("entityId"));
            } else if (context.getActiveScene().findEntityById(id) == null) {
                errors.add("Entity with ID '" + id + "' not found in active scene");
            }
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        return null; // Read-only
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = validateParameters(parameters, context);
        if (!errors.isEmpty()) {
            return AgentToolResult.failure(errors.get(0));
        }
        Entity entity = parameters != null && parameters.get("entityId") != null
            ? context.getActiveScene().findEntityById(parseId(parameters.get("entityId")))
            : context.getEditorState().getSelectedEntity();
        ModelingHandOff.Notice notice = ModelingHandOff.noticeFor(entity);
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("entityId", entity == null ? "" : entity.getId().toString());
        outputs.put("name", entity == null ? "" : entity.getName());
        outputs.put("capability", notice.capability().name().toLowerCase(Locale.ROOT).replace('_', '-'));
        outputs.put("meshEditing", notice.meshEditing());
        outputs.put("sculptCopy", notice.sculptCopy());
        outputs.put("materialSlot", notice.materialSlot());
        outputs.put("model", notice.modelPath() == null ? "" : notice.modelPath());
        outputs.put("title", notice.title());
        outputs.put("notice", notice.lines());
        return AgentToolResult.success(notice.summary(), outputs);
    }

    private static UUID parseId(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null; // reported by validateParameters as an invalid id
        }
    }
}
