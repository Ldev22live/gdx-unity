package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.agent.AgentTool;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolContext;
import io.github.ldev22live.gdxunity.editor.core.agent.AgentToolResult;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;

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
 * Writes the level ground's heights as a 16-bit PNG heightmap inside the project (roadmap M3.5): the assistant's form of Export
 * Heightmap. It changes nothing in the scene, so it is read-only for the plan's undo; the file it writes is the result.
 *
 * <p>Parameter: {@code path}, a project-relative {@code .png}. Outputs: {@code path}, {@code width}, {@code height}, {@code lowest},
 * {@code highest} (the height range the samples stand for).
 */
public class TerrainHeightmapExportTool implements AgentTool {
    public static final String ID = "terrain_heightmap_export";

    private final LevelSession level;

    public TerrainHeightmapExportTool(LevelSession level) {
        this.level = Objects.requireNonNull(level, "level cannot be null");
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDescription() {
        return "Writes the level ground's heights as a 16-bit PNG heightmap (with its height range) to path, a .png inside the project. "
            + "Changes nothing in the scene. Outputs path, width, height, lowest and highest.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public List<String> validateParameters(Map<String, Object> parameters, AgentToolContext context) {
        List<String> errors = new ArrayList<>();
        if (level.findTerrain() == null) {
            errors.add("There is no level ground: create one with New Level first");
        }
        Path file = target(parameters);
        if (file == null) {
            errors.add("'path' must be a .png inside the project: " + (parameters == null ? null : parameters.get("path")));
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
        Terrain terrain = level.findTerrain();
        Path file = target(parameters);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, Heightmaps.export(terrain));
        } catch (IOException e) {
            return AgentToolResult.failure("Cannot write " + parameters.get("path") + ": " + e.getMessage());
        }
        float[] range = terrain.heightRange();
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("path", parameters.get("path").toString());
        outputs.put("width", terrain.getResolutionX());
        outputs.put("height", terrain.getResolutionZ());
        outputs.put("lowest", range[0]);
        outputs.put("highest", range[1]);
        return AgentToolResult.success("Exported the heightmap " + parameters.get("path"), outputs);
    }

    private Path target(Map<String, Object> parameters) {
        Object value = parameters == null ? null : parameters.get("path");
        if (value == null || !value.toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            return null;
        }
        Path root = level.getAssetRoot().toPath().toAbsolutePath().normalize();
        Path file = root.resolve(value.toString()).normalize();
        return file.startsWith(root) && !file.equals(root) ? file : null;
    }
}
