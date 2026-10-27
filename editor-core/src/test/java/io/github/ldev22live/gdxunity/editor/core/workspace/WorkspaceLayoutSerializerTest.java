package io.github.ldev22live.gdxunity.editor.core.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceLayoutSerializerTest {

    @Test
    void shouldSerializeAndDeserializeValidConfigRoundTrip() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        WorkspaceLayoutConfig original = new WorkspaceLayoutConfig();
        original.setActiveWorkspaceId("vector-studio");
        original.setSplitRatioMain(0.75f);
        original.setSplitRatioLeft(0.3f);
        original.setSplitRatioRight(0.2f);
        original.setBottomPanelCollapsed(true);

        String json = serializer.serialize(original);
        assertNotNull(json);
        assertTrue(json.contains("vector-studio"));

        WorkspaceLayoutConfig loaded = serializer.deserialize(json);
        assertNotNull(loaded);
        assertEquals(1, loaded.getVersion());
        assertEquals("vector-studio", loaded.getActiveWorkspaceId());
        assertEquals(0.75f, loaded.getSplitRatioMain(), 0.001f);
        assertEquals(0.3f, loaded.getSplitRatioLeft(), 0.001f);
        assertEquals(0.2f, loaded.getSplitRatioRight(), 0.001f);
        assertTrue(loaded.isBottomPanelCollapsed());
    }

    @Test
    void shouldRecoverDefaultWhenJsonIsCorruptedOrEmpty() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();

        WorkspaceLayoutConfig empty = serializer.deserialize("");
        assertNotNull(empty);
        assertEquals("scene-editor", empty.getActiveWorkspaceId());

        WorkspaceLayoutConfig corrupted = serializer.deserialize("{ invalid json: broken ");
        assertNotNull(corrupted);
        assertEquals("scene-editor", corrupted.getActiveWorkspaceId());
        assertEquals(0.72f, corrupted.getSplitRatioMain(), 0.001f);
    }

    @Test
    void shouldFallbackWhenSchemaVersionIsUnsupported() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        String futureVersionJson = "{\"version\": 999, \"activeWorkspaceId\": \"future-studio\"}";

        WorkspaceLayoutConfig config = serializer.deserialize(futureVersionJson);
        assertNotNull(config);
        assertEquals(1, config.getVersion());
        assertEquals("scene-editor", config.getActiveWorkspaceId());
    }

    @Test
    void shouldClampOutOfRangeSplitRatiosSafely() {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        WorkspaceLayoutConfig extreme = new WorkspaceLayoutConfig();
        extreme.setSplitRatioMain(99.0f); // Out of [0.1, 0.9]
        extreme.setSplitRatioLeft(-5.0f); // Out of [0.1, 0.5]

        String json = serializer.serialize(extreme);
        WorkspaceLayoutConfig clamped = serializer.deserialize(json);

        assertEquals(0.9f, clamped.getSplitRatioMain(), 0.001f);
        assertEquals(0.1f, clamped.getSplitRatioLeft(), 0.001f);
    }

    @Test
    void shouldSaveAndLoadFromFileSafely(@TempDir Path tempDir) throws IOException {
        WorkspaceLayoutSerializer serializer = new WorkspaceLayoutSerializer();
        File file = tempDir.resolve("workspace_layout.json").toFile();

        WorkspaceLayoutConfig config = serializer.createDefaultConfig();
        config.setActiveWorkspaceId("audio-studio");
        config.setSplitRatioMain(0.65f);

        boolean saved = serializer.saveToFile(config, file);
        assertTrue(saved);
        assertTrue(file.exists());

        WorkspaceLayoutConfig loaded = serializer.loadFromFile(file);
        assertNotNull(loaded);
        assertEquals("audio-studio", loaded.getActiveWorkspaceId());
        assertEquals(0.65f, loaded.getSplitRatioMain(), 0.001f);

        // Non-existent file should load defaults
        File nonExistent = tempDir.resolve("missing.json").toFile();
        WorkspaceLayoutConfig def = serializer.loadFromFile(nonExistent);
        assertNotNull(def);
        assertEquals("scene-editor", def.getActiveWorkspaceId());
    }
}
