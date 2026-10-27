package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.ChangeTransformCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.gizmo.TransformSnapshot;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.Transform;

import java.util.*;

/**
 * Tool to modify an entity's local position, rotation, or scale within safe numeric limits.
 */
public class TransformSetTool implements AgentTool {
    public static final String ID = "transform_set";

    public static final float MIN_POSITION = -100_000.0f;
    public static final float MAX_POSITION = 100_000.0f;
    public static final float MIN_SCALE = 0.0001f;
    public static final float MAX_SCALE = 10_000.0f;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Updates local transform position, rotation, and/or scale of an entity within safe numeric limits.";
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

        validateNumericRange(parameters, "posX", MIN_POSITION, MAX_POSITION, errors);
        validateNumericRange(parameters, "posY", MIN_POSITION, MAX_POSITION, errors);
        validateNumericRange(parameters, "posZ", MIN_POSITION, MAX_POSITION, errors);

        validateNumericRange(parameters, "scaleX", MIN_SCALE, MAX_SCALE, errors);
        validateNumericRange(parameters, "scaleY", MIN_SCALE, MAX_SCALE, errors);
        validateNumericRange(parameters, "scaleZ", MIN_SCALE, MAX_SCALE, errors);

        return errors;
    }

    private void validateNumericRange(Map<String, Object> params, String key, float min, float max, List<String> errors) {
        if (params != null && params.containsKey(key)) {
            Object val = params.get(key);
            if (val instanceof Number) {
                float f = ((Number) val).floatValue();
                if (Float.isNaN(f) || Float.isInfinite(f) || f < min || f > max) {
                    errors.add("Parameter '" + key + "' must be finite between " + min + " and " + max);
                }
            } else {
                errors.add("Parameter '" + key + "' must be a numeric value");
            }
        }
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        UUID id = parseUUID(parameters.get("entityId"));
        Scene scene = context.getActiveScene();
        Entity entity = (scene != null) ? scene.findEntityById(id) : null;
        if (entity == null) return null;

        Transform t = entity.getTransform();
        TransformSnapshot before = TransformSnapshot.capture(t);

        Vector3 targetPos = new Vector3(t.getLocalPosition());
        if (parameters.containsKey("posX")) targetPos.x = ((Number) parameters.get("posX")).floatValue();
        if (parameters.containsKey("posY")) targetPos.y = ((Number) parameters.get("posY")).floatValue();
        if (parameters.containsKey("posZ")) targetPos.z = ((Number) parameters.get("posZ")).floatValue();

        Quaternion targetRot = new Quaternion(t.getLocalRotation());
        if (parameters.containsKey("rotZ")) {
            float degrees = ((Number) parameters.get("rotZ")).floatValue();
            targetRot.set(Vector3.Z, degrees);
        }

        Vector3 targetScale = new Vector3(t.getLocalScale());
        if (parameters.containsKey("scaleX")) targetScale.x = ((Number) parameters.get("scaleX")).floatValue();
        if (parameters.containsKey("scaleY")) targetScale.y = ((Number) parameters.get("scaleY")).floatValue();
        if (parameters.containsKey("scaleZ")) targetScale.z = ((Number) parameters.get("scaleZ")).floatValue();

        TransformSnapshot after = new TransformSnapshot(
            targetPos, targetRot, targetScale,
            before.worldPosition(), before.worldRotation(), before.worldScale(),
            before.parentWorldPosition(), before.parentWorldRotation(), before.parentWorldScale()
        );

        return new ChangeTransformCommand(context.getEditorState(), id, before, after, "Agent Set Transform");
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        EditorCommand cmd = createCommand(parameters, context);
        if (cmd == null) {
            return AgentToolResult.failure("Entity not found to transform");
        }
        boolean success = cmd.execute();
        if (!success) {
            return AgentToolResult.failure("Transform unchanged or execution failed");
        }
        return AgentToolResult.success("Updated transform for entity " + parameters.get("entityId"));
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
