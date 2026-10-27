package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import com.badlogic.gdx.graphics.Color;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;

import java.util.*;

/**
 * Tool to attach supported components (e.g. ShapeRendererComponent) to an entity with validated properties.
 */
public class ComponentAddTool implements AgentTool {
    public static final String ID = "component_add";

    private static final Set<String> ALLOWED_COMPONENTS = Set.of(
        "ShapeRendererComponent"
    );

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Attaches an allowed component (e.g. ShapeRendererComponent) with validated properties to an entity.";
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

        if (parameters == null || !parameters.containsKey("componentType") || parameters.get("componentType") == null) {
            errors.add("Missing required parameter 'componentType'");
        } else {
            String type = parameters.get("componentType").toString();
            if (!ALLOWED_COMPONENTS.contains(type)) {
                errors.add("Unsupported componentType '" + type + "'. Allowed types: " + ALLOWED_COMPONENTS);
            }
        }

        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        UUID id = parseUUID(parameters.get("entityId"));
        String type = parameters.get("componentType").toString();

        Component component = null;
        if ("ShapeRendererComponent".equals(type)) {
            float width = 100f;
            float height = 100f;
            Color color = Color.WHITE;

            if (parameters.containsKey("properties") && parameters.get("properties") instanceof Map<?, ?>) {
                Map<?, ?> props = (Map<?, ?>) parameters.get("properties");
                if (props.containsKey("width") && props.get("width") instanceof Number) {
                    width = ((Number) props.get("width")).floatValue();
                }
                if (props.containsKey("height") && props.get("height") instanceof Number) {
                    height = ((Number) props.get("height")).floatValue();
                }
                if (props.containsKey("color") && props.get("color") != null) {
                    try {
                        color = Color.valueOf(props.get("color").toString());
                    } catch (Exception ignored) {}
                }
            }
            component = new ShapeRendererComponent(width, height, color);
        }

        if (component == null) {
            throw new IllegalArgumentException("Failed to construct component for type: " + type);
        }

        return new AddComponentCommand(context.getEditorState(), id, component);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        EditorCommand cmd = createCommand(parameters, context);
        boolean success = cmd.execute();
        if (!success) {
            return AgentToolResult.failure("Failed to add component to entity");
        }
        return AgentToolResult.success("Added " + parameters.get("componentType") + " to entity " + parameters.get("entityId"));
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
