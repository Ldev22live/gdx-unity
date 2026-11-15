package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssets;
import io.github.ldev22live.gdxunity.editor.core.material.MaterialAssignment;
import io.github.ldev22live.gdxunity.engine.material.MaterialLibrary;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Puts a {@code .material} on an entity's material slot, or empties it (roadmap M2.2; the agent's version of dropping a material from
 * the Content Browser, rule 8 of the milestone chart). Parameters: {@code entityId}, and {@code materialPath}, a project-relative
 * {@code .material} path or blank for none. Refused, with the reason, for an entity with no material slot or a file that cannot be read.
 */
public class MaterialAssignTool implements AgentTool {
    public static final String ID = "material_assign";
    private static final String ENTITY = "entityId";
    private static final String PATH = "materialPath";

    private final MaterialLibrary library;

    /** @param library the project's materials, used to check that a file can be read */
    public MaterialAssignTool(MaterialLibrary library) {
        this.library = Objects.requireNonNull(library, "library cannot be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Applies a .material file to a shape entity's material slot (entityId, materialPath; a blank materialPath removes it).";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        Entity entity = null;
        if (parameters == null || parameters.get(ENTITY) == null) {
            errors.add("Missing required parameter '" + ENTITY + "'");
        } else {
            UUID id = parseUuid(parameters.get(ENTITY));
            entity = id == null || context.getActiveScene() == null ? null : context.getActiveScene().findEntityById(id);
            if (id == null) {
                errors.add("Invalid UUID format for '" + ENTITY + "'");
            } else if (entity == null) {
                errors.add("Entity with ID '" + id + "' not found in active scene");
            } else if (!MaterialAssignment.hasSlot(entity)) {
                errors.add(entity.getName() + " has no material slot: only shapes (Mesh Primitive) take a material so far");
            }
        }
        if (parameters == null || !parameters.containsKey(PATH)) {
            errors.add("Missing required parameter '" + PATH + "' (blank for none)");
        } else {
            String path = path(parameters);
            if (!path.isEmpty() && !MaterialAssets.isMaterialPath(path)) {
                errors.add("'" + PATH + "' must be a " + MaterialAssets.EXTENSION + " file: " + path);
            } else if (!path.isEmpty() && library.get(path) == null) {
                errors.add(library.getError(path));
            }
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        Entity entity = context.getActiveScene().findEntityById(parseUuid(parameters.get(ENTITY)));
        MeshPrimitiveComponent shape = entity.getComponent(MeshPrimitiveComponent.class);
        String path = path(parameters);
        return new ModifyComponentPropertyCommand<MeshPrimitiveComponent, String>(context.getEditorState(), entity.getId(), shape,
            shape.getMaterialPath(), path, shape::setMaterialPath, path.isEmpty() ? "Remove Material" : "Apply Material " + path);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        EditorCommand command = createCommand(parameters, context);
        if (!command.execute()) {
            return AgentToolResult.failure("Failed to apply the material");
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put(ENTITY, parameters.get(ENTITY).toString());
        outputs.put(PATH, path(parameters));
        return AgentToolResult.success(path(parameters).isEmpty() ? "Removed the material" : "Applied " + path(parameters), outputs);
    }

    private static String path(Map<String, Object> parameters) {
        Object value = parameters.get(PATH);
        return value == null ? "" : value.toString().trim();
    }

    private static UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
