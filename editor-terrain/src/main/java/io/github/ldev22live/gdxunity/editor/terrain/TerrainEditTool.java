package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.terrain.Surface;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainBrush;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainDelta;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainStroke;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The assistant's terrain tool (roadmap M3.4, TD-089, chart rule 8): one brush dab on the level's ground, or its water level, as one
 * undo step that writes the {@code .terrain} file, exactly as the level editor's tools do.
 *
 * <p>Parameters: {@code action} (raise, lower, smooth, flatten, noise, paint, hole, erode, ramp, water or heightmap); for a brush,
 * {@code x} and {@code z} in the ground's own space, optional {@code radius} and {@code strength}, {@code inverted} (Ctrl: lower, wipe
 * paint, fill holes) and, for paint, {@code surface} (grass, sand, snow, gravel, rock or dirt); for a ramp also {@code x2} and
 * {@code z2}, where it ends (its width is twice the radius; roadmap M3.5); for water, {@code level} (a number, or "none" to remove it);
 * for heightmap, {@code path}, a PNG in the project whose heights are put on the ground (roadmap M3.5). Outputs:
 * {@code terrain} (the file), {@code action}, and for a brush {@code changedHeights}, {@code changedWeights} and {@code changedHoles};
 * for water {@code waterLevel} (a number, or "none").
 */
public class TerrainEditTool implements AgentTool {
    public static final String ID = "terrain_edit";
    private static final List<String> BRUSHES = List.of("raise", "lower", "smooth", "flatten", "noise", "paint", "hole", "erode", "ramp");

    private final LevelSession level;

    public TerrainEditTool(LevelSession level) {
        this.level = Objects.requireNonNull(level, "level cannot be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Edits the level's ground as one undo step: action raise, lower, smooth, flatten, noise, paint (with surface grass, sand, "
            + "snow, gravel, rock or dirt; soft edges), hole or erode, at x and z (the ground's own space) with optional radius, strength "
            + "and inverted (lower, wipe paint, fill holes); action ramp from x, z to x2, z2 (an even slope between their heights, twice "
            + "the radius wide); action water with level (a number, or none to remove it); action heightmap with path (a PNG in the "
            + "project whose heights replace the ground's). Needs a level ground (New Level).";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        Terrain terrain = level.findTerrain();
        if (level.findGround() == null || terrain == null) {
            errors.add("There is no level ground: create one with New Level first");
            return errors;
        }
        String action = text(parameters, "action");
        if (action == null || !(BRUSHES.contains(action) || action.equals("water") || action.equals("heightmap"))) {
            errors.add("'action' must be one of " + String.join(", ", BRUSHES) + ", water or heightmap: " + action);
            return errors;
        }
        if (action.equals("heightmap")) {
            File file = projectFile(parameters);
            if (file == null || !file.isFile()) {
                errors.add("'path' must name a PNG heightmap inside the project: " + (parameters == null ? null : parameters.get("path")));
            }
            return errors;
        }
        if (action.equals("water")) {
            Object value = parameters.get("level");
            if (value == null || !(value instanceof Number || "none".equalsIgnoreCase(value.toString()))) {
                errors.add("'level' must be a number, or none to remove the water: " + value);
            }
            return errors;
        }
        Float x = number(parameters, "x");
        Float z = number(parameters, "z");
        if (x == null || z == null) {
            errors.add("'x' and 'z' are required numbers, in the ground's own space");
        } else if (Float.isNaN(terrain.heightAt(x, z))) {
            errors.add(String.format(Locale.ROOT, "%.1f, %.1f is off the ground, which spans %.1f by %.1f around its centre", x, z,
                terrain.getSizeX(), terrain.getSizeZ()));
        }
        if (action.equals("ramp")) {
            Float x2 = number(parameters, "x2");
            Float z2 = number(parameters, "z2");
            if (x2 == null || z2 == null || Float.isNaN(terrain.heightAt(x2, z2))) {
                errors.add("A ramp needs 'x2' and 'z2' on the ground: where it ends");
            }
        }
        Float radius = number(parameters, "radius");
        if (parameters.containsKey("radius") && (radius == null || !(radius > 0f && radius <= TerrainBrush.MAX_RADIUS))) {
            errors.add("'radius' must be above 0 and at most " + TerrainBrush.MAX_RADIUS + ": " + parameters.get("radius"));
        }
        Float strength = number(parameters, "strength");
        if (parameters.containsKey("strength") && (strength == null || !(strength >= 0f && strength <= 1f))) {
            errors.add("'strength' must be within 0 to 1: " + parameters.get("strength"));
        }
        if (action.equals("paint") && !parameters.containsKey("inverted")) {
            Surface surface = surface(parameters);
            if (surface == null) {
                errors.add("'surface' must be grass, sand, snow, gravel, rock or dirt: " + parameters.get("surface"));
            }
        }
        return errors;
    }

    @Override
    public EditorCommand createCommand(Map<String, Object> parameters, AgentToolContext context) {
        Entity ground = level.findGround();
        Terrain terrain = level.findTerrain();
        String path = TerrainSession.componentOf(ground).getTerrainPath();
        String action = text(parameters, "action");
        if (action.equals("water")) {
            Object value = parameters.get("level");
            float target = value instanceof Number n ? n.floatValue() : Float.NaN;
            if (Float.compare(terrain.getWaterLevel(), target) == 0) {
                return null;
            }
            return new TerrainWaterCommand(level.getTerrainSession().getLibrary(), path, terrain, target);
        }
        if (action.equals("heightmap")) {
            HeightmapPng.Heightmap map;
            try {
                map = HeightmapPng.read(Files.readAllBytes(projectFile(parameters).toPath()));
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read the heightmap: " + e.getMessage(), e);
            }
            TerrainDelta imported = Heightmaps.importInto(terrain, map);
            if (imported.isEmpty()) {
                return null;
            }
            terrain.apply(imported, false);
            return new TerrainStrokeCommand(level.getTerrainSession().getLibrary(), path, terrain, imported, "Terrain: Import Heightmap");
        }
        TerrainBrush brush = level.getTerrainSession().getBrush().copy();
        brush.setType(TerrainBrush.Type.valueOf(action.toUpperCase(Locale.ROOT)));
        Float radius = number(parameters, "radius");
        if (radius != null) {
            brush.setRadius(radius);
        }
        Float strength = number(parameters, "strength");
        if (strength != null) {
            brush.setStrength(strength);
        }
        brush.setInverted(Boolean.parseBoolean(String.valueOf(parameters.getOrDefault("inverted", "false"))));
        Surface surface = surface(parameters);
        if (surface != null) {
            brush.setSurface(surface);
        }
        // The stroke changes the terrain as it goes; take the change and put the terrain back, so the command does the edit
        TerrainStroke stroke = new TerrainStroke(terrain);
        stroke.begin(brush, number(parameters, "x"), number(parameters, "z"));
        if (action.equals("ramp")) {
            stroke.move(number(parameters, "x2"), number(parameters, "z2"));
        }
        TerrainDelta delta = stroke.end();
        if (delta.isEmpty()) {
            return null;
        }
        terrain.apply(delta, false);
        return new TerrainStrokeCommand(level.getTerrainSession().getLibrary(), path, terrain, delta,
            "Terrain: " + capitalized(action));
    }

    @Override
    public AgentToolResult execute(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = validateParameters(parameters, context);
        if (!errors.isEmpty()) {
            return AgentToolResult.failure(errors.get(0));
        }
        EditorCommand command = createCommand(parameters, context);
        String action = text(parameters, "action");
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("terrain", TerrainSession.componentOf(level.findGround()).getTerrainPath());
        outputs.put("action", action);
        if (command == null) {
            outputs.put("changed", false);
            return AgentToolResult.success("Nothing changed", outputs);
        }
        if (!context.getEditorState().executeCommand(command)) {
            return AgentToolResult.failure("The editor refused the change: " + context.getEditorState().getStatusMessage());
        }
        outputs.put("changed", true);
        if (command instanceof TerrainStrokeCommand) {
            Terrain terrain = level.findTerrain();
            outputs.put("hasPaint", terrain.hasWeights());
            outputs.put("hasHoles", terrain.hasHoles());
        } else {
            float water = level.findTerrain().getWaterLevel();
            outputs.put("waterLevel", Float.isNaN(water) ? "none" : water);
        }
        return AgentToolResult.success(command.getDescription(), outputs);
    }

    /** The {@code path} parameter as a file inside the project, or null when it names none or leaves the project. */
    private File projectFile(Map<String, Object> parameters) {
        Object value = parameters == null ? null : parameters.get("path");
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        Path root = level.getAssetRoot().toPath().toAbsolutePath().normalize();
        Path file = root.resolve(value.toString()).normalize();
        return file.startsWith(root) ? file.toFile() : null;
    }

    private static String text(Map<String, Object> parameters, String key) {
        Object value = parameters == null ? null : parameters.get(key);
        return value == null ? null : value.toString().trim().toLowerCase(Locale.ROOT);
    }

    private static Float number(Map<String, Object> parameters, String key) {
        Object value = parameters == null ? null : parameters.get(key);
        if (value instanceof Number n) {
            return n.floatValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Float.parseFloat(value.toString());
        } catch (NumberFormatException e) {
            return null; // reported by validateParameters as not a number
        }
    }

    private static Surface surface(Map<String, Object> parameters) {
        String name = text(parameters, "surface");
        if (name == null) {
            return null;
        }
        for (Surface surface : Surface.values()) {
            if (surface != Surface.AUTO && surface.name().equalsIgnoreCase(name)) {
                return surface;
            }
        }
        return null;
    }

    private static String capitalized(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
