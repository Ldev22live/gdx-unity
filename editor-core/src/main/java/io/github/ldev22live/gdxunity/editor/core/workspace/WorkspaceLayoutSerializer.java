package io.github.ldev22live.gdxunity.editor.core.workspace;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Handles versioned serialization, validation, and safe default recovery for workspace layout configurations.
 */
public class WorkspaceLayoutSerializer {
    /**
     * Version 2 (TD-085) adds which panels are open. A version 1 file keeps its sizes and gets the World layout's panels (Outliner and
     * the bottom panel closed, Details with the selection), so the World-first layout is what an existing install sees next.
     */
    public static final int CURRENT_VERSION = 2;
    private static final int VERSION_WITHOUT_PANELS = 1;
    private final Json json;

    public WorkspaceLayoutSerializer() {
        this.json = new Json();
        this.json.setOutputType(JsonWriter.OutputType.json);
        this.json.setIgnoreUnknownFields(true);
    }

    /**
     * Serializes a layout configuration to JSON string.
     */
    public String serialize(WorkspaceLayoutConfig config) {
        if (config == null) {
            config = createDefaultConfig();
        }
        config.setVersion(CURRENT_VERSION);
        validateAndClamp(config);
        return json.prettyPrint(config);
    }

    /**
     * Deserializes a layout configuration from JSON string with graceful fallback.
     */
    public WorkspaceLayoutConfig deserialize(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return createDefaultConfig();
        }
        try {
            WorkspaceLayoutConfig config = json.fromJson(WorkspaceLayoutConfig.class, jsonString);
            if (config == null) {
                return createDefaultConfig();
            }
            if (config.getVersion() == VERSION_WITHOUT_PANELS) {
                WorkspaceLayoutConfig defaults = createDefaultConfig();
                config.setBottomPanelCollapsed(defaults.isBottomPanelCollapsed());
                config.setOutlinerShown(defaults.isOutlinerShown());
                config.setDetailsMode(defaults.getDetailsMode());
                config.setVersion(CURRENT_VERSION);
            } else if (config.getVersion() != CURRENT_VERSION) {
                // If unsupported schema version, return safe default
                return createDefaultConfig();
            }
            validateAndClamp(config);
            return config;
        } catch (Exception e) {
            // Corrupted JSON or parsing failure -> safe fallback to factory default
            return createDefaultConfig();
        }
    }

    /**
     * Loads a layout configuration from a file, recovering to default on error.
     */
    public WorkspaceLayoutConfig loadFromFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return createDefaultConfig();
        }
        try {
            String content = Files.readString(file.toPath());
            return deserialize(content);
        } catch (Exception e) {
            return createDefaultConfig();
        }
    }

    /**
     * Saves a layout configuration to file atomically or safely.
     */
    public boolean saveToFile(WorkspaceLayoutConfig config, File file) {
        if (file == null) return false;
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            String content = serialize(config);
            Files.writeString(file.toPath(), content);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public WorkspaceLayoutConfig createDefaultConfig() {
        WorkspaceLayoutConfig config = new WorkspaceLayoutConfig();
        config.setVersion(CURRENT_VERSION);
        config.setActiveWorkspaceId("scene-editor");
        config.setSplitRatioMain(0.72f);
        config.setSplitRatioLeft(0.20f);
        config.setSplitRatioRight(0.25f);
        config.setBottomPanelCollapsed(true);
        config.setOutlinerShown(false);
        config.setDetailsMode(PanelLayout.DetailsMode.WITH_SELECTION.name());
        return config;
    }

    private void validateAndClamp(WorkspaceLayoutConfig config) {
        if (config.getActiveWorkspaceId() == null || config.getActiveWorkspaceId().trim().isEmpty()) {
            config.setActiveWorkspaceId("scene-editor");
        }
        config.setSplitRatioMain(clamp(config.getSplitRatioMain(), 0.1f, 0.9f, 0.8f));
        config.setSplitRatioLeft(clamp(config.getSplitRatioLeft(), 0.1f, 0.5f, 0.25f));
        config.setSplitRatioRight(clamp(config.getSplitRatioRight(), 0.1f, 0.5f, 0.25f));
        if (PanelLayout.DetailsMode.parse(config.getDetailsMode()).isEmpty()) {
            config.setDetailsMode(PanelLayout.DetailsMode.WITH_SELECTION.name());
        }
    }

    private float clamp(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
