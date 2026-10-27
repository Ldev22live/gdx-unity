package io.github.ldev22live.gdxunity.editor.core;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorStateTest {

    @Test
    void shouldInitializeWithDefaultSceneAndSelection() {
        EditorState state = new EditorState();

        assertNotNull(state.getActiveScene());
        assertNull(state.getSelectedEntityId());
        assertNull(state.getSelectedEntity());
        assertEquals(ToolMode.SELECT, state.getToolMode());
        assertEquals("Ready", state.getStatusMessage());
        assertFalse(state.isStatusError());
    }

    @Test
    void shouldUpdateSelectionAndNotifyListeners() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("TestEntity");

        AtomicInteger selectionEvents = new AtomicInteger(0);
        state.addListener(new EditorState.Listener() {
            @Override
            public void onSelectionChanged(UUID previousSelectedId, UUID newSelectedId) {
                selectionEvents.incrementAndGet();
            }
        });

        assertEquals(entity, state.getSelectedEntity());

        // Deselect
        state.clearSelection();
        assertNull(state.getSelectedEntity());
        assertEquals(1, selectionEvents.get());

        // Reselect
        state.setSelectedEntity(entity);
        assertEquals(entity, state.getSelectedEntity());
        assertEquals(2, selectionEvents.get());
    }

    @Test
    void shouldClearSelectionWhenSelectedEntityIsDeleted() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("Victim");
        assertEquals(entity, state.getSelectedEntity());

        boolean deleted = state.deleteSelectedEntity();
        assertTrue(deleted);
        assertNull(state.getSelectedEntity());
        assertNull(state.getActiveScene().findEntityById(entity.getId()));
    }

    @Test
    void shouldClearSelectionWhenSceneSwitches() {
        EditorState state = new EditorState();
        Entity entity = state.createEntity("Entity1");
        assertEquals(entity, state.getSelectedEntity());

        Scene newScene = new Scene("NewScene");
        state.setActiveScene(newScene);

        assertEquals(newScene, state.getActiveScene());
        assertNull(state.getSelectedEntity());
    }

    @Test
    void shouldSaveAndLoadSceneToFile(@TempDir Path tempDir) throws IOException {
        EditorState state = new EditorState();
        Entity parent = state.createEntity("ParentNode");
        parent.getTransform().setLocalPosition(100f, 200f, 0f);

        Entity child = state.createEntity("ChildNode");
        child.getTransform().setParent(parent.getTransform());

        File saveFile = tempDir.resolve("test_scene.json").toFile();
        state.saveSceneToFile(saveFile);
        assertTrue(saveFile.exists());

        // Create new empty scene then load back
        state.newScene("Empty");
        assertEquals(0, state.getActiveScene().getEntityCount());

        state.loadSceneFromFile(saveFile);
        assertEquals(2, state.getActiveScene().getEntityCount());

        Entity loadedParent = state.getActiveScene().findEntityByName("ParentNode");
        assertNotNull(loadedParent);
        assertEquals(100f, loadedParent.getTransform().getLocalPosition().x);

        Entity loadedChild = state.getActiveScene().findEntityByName("ChildNode");
        assertNotNull(loadedChild);
        assertEquals(loadedParent.getTransform(), loadedChild.getTransform().getParent());
    }

    @Test
    void shouldChangeToolModeAndNotify() {
        EditorState state = new EditorState();
        AtomicBoolean toolModeChanged = new AtomicBoolean(false);
        state.addListener(new EditorState.Listener() {
            @Override
            public void onToolModeChanged(ToolMode oldMode, ToolMode newMode) {
                if (newMode == ToolMode.MOVE) {
                    toolModeChanged.set(true);
                }
            }
        });

        state.setToolMode(ToolMode.MOVE);
        assertEquals(ToolMode.MOVE, state.getToolMode());
        assertTrue(toolModeChanged.get());
    }

    @Test
    void shouldTrackSelectedAssetAndNotify() {
        EditorState state = new EditorState();
        assertNull(state.getSelectedAsset());

        io.github.ldev22live.gdxunity.engine.core.asset.AssetReference asset =
            new io.github.ldev22live.gdxunity.engine.core.asset.AssetReference("textures/player.png");

        AtomicInteger assetEvents = new AtomicInteger(0);
        state.addListener(new EditorState.Listener() {
            @Override
            public void onSelectedAssetChanged(
                io.github.ldev22live.gdxunity.engine.core.asset.AssetReference previousAsset,
                io.github.ldev22live.gdxunity.engine.core.asset.AssetReference newAsset
            ) {
                assetEvents.incrementAndGet();
            }
        });

        state.setSelectedAsset(asset);
        assertEquals(asset, state.getSelectedAsset());
        assertEquals(1, assetEvents.get());

        state.setSelectedAsset(null);
        assertNull(state.getSelectedAsset());
        assertEquals(2, assetEvents.get());
    }
}
