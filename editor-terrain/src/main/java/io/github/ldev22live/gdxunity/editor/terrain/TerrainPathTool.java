package io.github.ldev22live.gdxunity.editor.terrain;

import com.badlogic.gdx.math.Vector2;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.CompoundCommand;
import io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainPathComponent;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainPath;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The assistant's road, river and cliff tool (roadmap M3.7): makes a Terrain Path entity through the given points and applies it
 * (carve, paint, place meshes beside it), all as one undo step, as the level editor's Path tool and Apply do.
 *
 * <p>Parameters: {@code points} (a list of [x, z] in the ground's own space, at least two, all on the ground); optional {@code kind}
 * (road, river or cliff; road by default), {@code width}, {@code depth}, {@code paint} (a surface, or none), {@code prefab} (a prefab
 * in the project placed beside it), {@code spacing} and {@code name}. Outputs: {@code name}, {@code kind}, {@code points}.
 */
public class TerrainPathTool implements AgentTool {
    public static final String ID = "terrain_path";

    private final LevelSession level;

    public TerrainPathTool(LevelSession level) {
        this.level = Objects.requireNonNull(level, "level cannot be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Makes a road, river or cliff on the level's ground along a curve through points (a list of [x, z] in the ground's own "
            + "space, at least two) and applies it as one undo step: optional kind (road, river or cliff), width, depth, paint (grass, "
            + "sand, snow, gravel, rock, dirt or none), prefab (placed beside it every spacing units) and name. Needs a level ground.";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        Terrain terrain = level.findTerrain();
        if (terrain == null) {
            errors.add("There is no level ground: create one with New Level first");
            return errors;
        }
        List<Vector2> points = points(parameters);
        if (points == null || points.size() < 2) {
            errors.add("'points' must be a list of at least two [x, z] pairs");
            return errors;
        }
        for (Vector2 point : points) {
            if (Float.isNaN(terrain.heightAt(point.x, point.y))) {
                errors.add(String.format(Locale.ROOT, "Point %.1f, %.1f is off the ground", point.x, point.y));
            }
        }
        try {
            component(parameters);
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }
        Object prefab = parameters.get("prefab");
        if (prefab != null && !prefab.toString().isBlank() && !new File(level.getAssetRoot(), prefab.toString()).isFile()) {
            errors.add("'prefab' names no file in the project: " + prefab);
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        TerrainPathComponent component = component(parameters);
        String name = parameters.get("name") == null ? kindName(component.getKind()) : parameters.get("name").toString();
        EditorCommand apply = level.applyPathCommand(component, name);
        if (apply == null) {
            return null;
        }
        CompoundCommand command = new CompoundCommand("Path: " + name);
        command.addCommand(new CreateEntityCommand(context.getEditorState(), name, null, List.<Component>of(component)));
        command.addCommand(apply);
        return command;
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) throws Exception {
        List<String> errors = validateParameters(parameters, context);
        if (!errors.isEmpty()) {
            return AgentToolResult.failure(errors.get(0));
        }
        EditorCommand command = createCommand(parameters, context);
        if (command == null) {
            return AgentToolResult.failure(context.getEditorState().getStatusMessage());
        }
        if (!context.getEditorState().executeCommand(command)) {
            return AgentToolResult.failure("The editor refused the path: " + context.getEditorState().getStatusMessage());
        }
        TerrainPathComponent component = component(parameters);
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("name", parameters.get("name") == null ? kindName(component.getKind()) : parameters.get("name").toString());
        outputs.put("kind", component.getKind().name().toLowerCase(Locale.ROOT));
        outputs.put("points", component.getPointCount());
        return AgentToolResult.success(command.getDescription(), outputs);
    }

    /** The path the parameters describe; throws with the reason when a value is wrong. */
    private static TerrainPathComponent component(Map<String, Object> parameters) {
        TerrainPathComponent component = new TerrainPathComponent();
        component.setPoints(points(parameters));
        Object kind = parameters.get("kind");
        if (kind != null) {
            try {
                component.setKind(TerrainPath.Kind.valueOf(kind.toString().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'kind' must be road, river or cliff: " + kind, e);
            }
        }
        Object paint = parameters.get("paint");
        if (paint != null) {
            String value = paint.toString().trim().toUpperCase(Locale.ROOT);
            try {
                component.setPaint(value.equals("NONE") ? Surface.AUTO : Surface.valueOf(value));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'paint' must be grass, sand, snow, gravel, rock, dirt or none: " + paint, e);
            }
        }
        if (parameters.get("width") instanceof Number width) {
            component.setWidth(width.floatValue());
        }
        if (parameters.get("depth") instanceof Number depth) {
            component.setDepth(depth.floatValue());
        }
        if (parameters.get("spacing") instanceof Number spacing) {
            component.setSpacing(spacing.floatValue());
        }
        if (parameters.get("prefab") != null) {
            component.setPrefabPath(parameters.get("prefab").toString());
        }
        return component;
    }

    /** The {@code points} parameter as points, or null when it is not a list of [x, z] pairs. */
    private static List<Vector2> points(Map<String, Object> parameters) {
        Object value = parameters == null ? null : parameters.get("points");
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Vector2> points = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof List<?> pair) || pair.size() != 2 || !(pair.get(0) instanceof Number x) || !(pair.get(1) instanceof Number z)) {
                return null;
            }
            points.add(new Vector2(x.floatValue(), z.floatValue()));
        }
        return points;
    }

    private static String kindName(TerrainPath.Kind kind) {
        return kind.name().charAt(0) + kind.name().substring(1).toLowerCase(Locale.ROOT);
    }
}
