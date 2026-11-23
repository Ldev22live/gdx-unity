package io.github.ldev22live.gdxunity.editor.core.agent.tools;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.environment.EnvironmentPresets;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.environment.AtmospherePreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessPreset;
import io.github.ldev22live.gdxunity.engine.scene.environment.PostProcessVolumeComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterBodyComponent;
import io.github.ldev22live.gdxunity.engine.scene.environment.WaterPreset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a sky, water or post-processing preset (roadmap M3.9, TD-090; M6.2, TD-091), the agent's version of the level palette's
 * Sky and Water rows and the preset menus in Details. Parameters:
 * <ul>
 *   <li>{@code target}: {@code sky}, {@code water} or {@code post}.</li>
 *   <li>{@code preset}: a sky preset (Clear Day, Overcast, Foggy, Sunset, Night, Rain, Snow), a water preset (Lake, Ocean,
 *       Swamp, Tropical, Icy) or a post-processing preset (None, Cinematic, Dreamy, Noir, Warm, Cold, Vivid).</li>
 *   <li>{@code entityId} (water and post, optional): the water body or Post Process Volume to change. Without it, water makes a new
 *       "Water" entity at {@code x}, {@code y}, {@code z} (each optional, default 0), and post changes the scene's global Post Process
 *       Volume, made when the scene has none.</li>
 * </ul>
 * One undo step. The sky goes on the scene's Sky and Weather, which is made when the scene has none.
 */
public class EnvironmentPresetTool implements AgentTool {
    public static final String ID = "environment_preset";
    private static final String TARGET = "target";
    private static final String PRESET = "preset";
    private static final String ENTITY = "entityId";
    private static final String SKY = "sky";
    private static final String WATER = "water";
    private static final String POST = "post";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Applies a sky preset (target sky: " + names(AtmospherePreset.values()) + "), a water preset (target water: "
            + names(WaterPreset.values()) + "; entityId of a water body, or none to add one at x, y, z) or a post-processing look "
            + "(target post: " + names(PostProcessPreset.values()) + "; entityId of a Post Process Volume, or none for the scene's "
            + "global one, made when missing). One undo step.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (context.getActiveScene() == null) {
            errors.add("No scene is open");
            return errors;
        }
        String target = text(parameters, TARGET).toLowerCase(Locale.ROOT);
        String preset = text(parameters, PRESET);
        if (!SKY.equals(target) && !WATER.equals(target) && !POST.equals(target)) {
            errors.add("'" + TARGET + "' must be 'sky', 'water' or 'post': '" + target + "'");
            return errors;
        }
        if (preset.isEmpty()) {
            errors.add("Missing required parameter '" + PRESET + "'");
        } else if (SKY.equals(target) && AtmospherePreset.find(preset).isEmpty()) {
            errors.add("Unknown sky preset '" + preset + "'; one of " + names(AtmospherePreset.values()));
        } else if (WATER.equals(target) && WaterPreset.find(preset).isEmpty()) {
            errors.add("Unknown water preset '" + preset + "'; one of " + names(WaterPreset.values()));
        } else if (POST.equals(target) && PostProcessPreset.find(preset).isEmpty()) {
            errors.add("Unknown post-processing preset '" + preset + "'; one of " + names(PostProcessPreset.values()));
        }
        if (!SKY.equals(target) && parameters.get(ENTITY) != null) {
            UUID id = parseUuid(parameters.get(ENTITY));
            Entity entity = id == null ? null : context.getActiveScene().findEntityById(id);
            if (id == null) {
                errors.add("Invalid UUID format for '" + ENTITY + "'");
            } else if (entity == null) {
                errors.add("Entity with ID '" + id + "' not found in active scene");
            } else if (WATER.equals(target) && entity.getComponent(WaterBodyComponent.class) == null) {
                errors.add(entity.getName() + " has no Water Body");
            } else if (POST.equals(target) && entity.getComponent(PostProcessVolumeComponent.class) == null) {
                errors.add(entity.getName() + " has no Post Process Volume");
            }
        }
        for (String axis : new String[] {"x", "y", "z"}) {
            Object value = parameters.get(axis);
            if (value != null && !(value instanceof Number)) {
                errors.add("'" + axis + "' must be a number: " + value);
            }
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        String preset = text(parameters, PRESET);
        String target = text(parameters, TARGET).toLowerCase(Locale.ROOT);
        if (SKY.equals(target)) {
            return EnvironmentPresets.applySky(context.getEditorState(), AtmospherePreset.find(preset).orElseThrow());
        }
        if (POST.equals(target)) {
            PostProcessPreset look = PostProcessPreset.find(preset).orElseThrow();
            if (parameters.get(ENTITY) != null) {
                Entity entity = context.getActiveScene().findEntityById(parseUuid(parameters.get(ENTITY)));
                return EnvironmentPresets.applyPostProcess(context.getEditorState(), entity, look);
            }
            return EnvironmentPresets.applyScenePostProcess(context.getEditorState(), look);
        }
        WaterPreset water = WaterPreset.find(preset).orElseThrow();
        if (parameters.get(ENTITY) != null) {
            Entity entity = context.getActiveScene().findEntityById(parseUuid(parameters.get(ENTITY)));
            return EnvironmentPresets.applyWater(context.getEditorState(), entity, water);
        }
        Vector3 position = new Vector3(number(parameters, "x"), number(parameters, "y"), number(parameters, "z"));
        return EnvironmentPresets.addWater(context.getEditorState(), position, water);
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        EditorCommand command = createCommand(parameters, context);
        if (!command.execute()) {
            return AgentToolResult.failure("Failed to apply the preset");
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put(TARGET, text(parameters, TARGET).toLowerCase(Locale.ROOT));
        outputs.put(PRESET, text(parameters, PRESET));
        outputs.put("sky", EnvironmentPresets.describeSky(context.getActiveScene()));
        outputs.put("post", EnvironmentPresets.describePostProcess(context.getActiveScene()));
        return AgentToolResult.success(command.getDescription(), outputs);
    }

    private static String text(Map<String, Object> parameters, String key) {
        Object value = parameters == null ? null : parameters.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static float number(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value instanceof Number n ? n.floatValue() : 0f;
    }

    private static String names(Enum<?>[] presets) {
        return String.join(", ", Arrays.stream(presets).map(p -> p instanceof AtmospherePreset a ? a.getDisplayName()
            : p instanceof PostProcessPreset post ? post.getDisplayName() : ((WaterPreset) p).getDisplayName()).toList());
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
