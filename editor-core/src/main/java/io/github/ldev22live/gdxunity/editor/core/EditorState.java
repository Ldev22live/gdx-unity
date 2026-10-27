package io.github.ldev22live.gdxunity.editor.core;

import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Authoritative state model for the editor.
 * Manages active scene, selected entity, tool mode, status messaging, and scene persistence.
 */
public class EditorState {
    public interface Listener {
        default void onActiveSceneChanged(Scene oldScene, Scene newScene) {}
        default void onSelectionChanged(UUID previousSelectedId, UUID newSelectedId) {}
        default void onMultiSelectionChanged(java.util.Set<UUID> previousSelection, java.util.Set<UUID> newSelection, UUID primarySelectedId) {}
        default void onToolModeChanged(ToolMode oldMode, ToolMode newMode) {}
        default void onStatusMessage(String message, boolean isError) {}
        default void onHierarchyModified() {}
        default void onEntityTransformModified(Entity entity) {}
        default void onSelectedAssetChanged(io.github.ldev22live.gdxunity.engine.core.asset.AssetReference previousAsset, io.github.ldev22live.gdxunity.engine.core.asset.AssetReference newAsset) {}
    }

    private Scene activeScene;
    private final java.util.Set<UUID> selectedEntityIds = new java.util.LinkedHashSet<>();
    private UUID primarySelectedEntityId;
    private io.github.ldev22live.gdxunity.engine.core.asset.AssetReference selectedAsset;
    private ToolMode toolMode = ToolMode.SELECT;
    private String statusMessage = "Ready";
    private boolean statusIsError = false;

    private final SceneSerializer serializer;
    private io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService;
    private io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager scriptManager;
    private final io.github.ldev22live.gdxunity.editor.core.command.CommandHistory commandHistory;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public EditorState() {
        this(new SceneSerializer(), null);
    }

    public EditorState(SceneSerializer serializer) {
        this(serializer, null);
    }

    public EditorState(SceneSerializer serializer, io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService) {
        this.serializer = Objects.requireNonNull(serializer, "serializer cannot be null");
        this.assetService = assetService;
        this.commandHistory = new io.github.ldev22live.gdxunity.editor.core.command.CommandHistory();
        this.activeScene = new Scene("Sample Scene");
    }

    public io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager getScriptManager() {
        return scriptManager;
    }

    public void setScriptManager(io.github.ldev22live.gdxunity.editor.core.scripting.ScriptManager scriptManager) {
        this.scriptManager = scriptManager;
    }

    public io.github.ldev22live.gdxunity.editor.core.command.CommandHistory getCommandHistory() {
        return commandHistory;
    }

    public boolean executeCommand(io.github.ldev22live.gdxunity.editor.core.command.EditorCommand command) {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot modify scene during Play/Pause mode", true);
            return false;
        }
        return commandHistory.execute(command);
    }

    public boolean undo() {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot undo during Play/Pause mode", true);
            return false;
        }
        return commandHistory.undo();
    }

    public boolean redo() {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot redo during Play/Pause mode", true);
            return false;
        }
        return commandHistory.redo();
    }

    public io.github.ldev22live.gdxunity.engine.core.asset.AssetService getAssetService() {
        return assetService;
    }

    public void setAssetService(io.github.ldev22live.gdxunity.engine.core.asset.AssetService assetService) {
        this.assetService = assetService;
    }

    public io.github.ldev22live.gdxunity.engine.core.asset.AssetReference getSelectedAsset() {
        return selectedAsset;
    }

    public void setSelectedAsset(io.github.ldev22live.gdxunity.engine.core.asset.AssetReference newAsset) {
        if (!Objects.equals(this.selectedAsset, newAsset)) {
            io.github.ldev22live.gdxunity.engine.core.asset.AssetReference prev = this.selectedAsset;
            this.selectedAsset = newAsset;
            for (Listener l : listeners) {
                l.onSelectedAssetChanged(prev, newAsset);
            }
        }
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public Scene getActiveScene() {
        return activeScene;
    }

    public void setActiveScene(Scene newScene) {
        if (this.activeScene == newScene) {
            return;
        }
        Scene oldScene = this.activeScene;
        this.activeScene = (newScene != null) ? newScene : new Scene("Untitled Scene");

        // Clear command history to prevent stale entity mutations across scene transitions
        this.commandHistory.clear();

        // Clear selection or reconcile
        java.util.Set<UUID> oldSelection = new java.util.LinkedHashSet<>(this.selectedEntityIds);
        UUID oldSelected = this.primarySelectedEntityId;
        this.selectedEntityIds.clear();
        this.primarySelectedEntityId = null;

        for (Listener l : listeners) {
            l.onActiveSceneChanged(oldScene, this.activeScene);
            if (oldSelected != null) {
                l.onSelectionChanged(oldSelected, null);
            }
            if (!oldSelection.isEmpty()) {
                l.onMultiSelectionChanged(oldSelection, java.util.Collections.emptySet(), null);
            }
        }
        setStatusMessage("Switched to scene: " + this.activeScene.getName(), false);
    }

    public UUID getSelectedEntityId() {
        return primarySelectedEntityId;
    }

    public Entity getSelectedEntity() {
        if (primarySelectedEntityId == null || activeScene == null) {
            return null;
        }
        return activeScene.findEntityById(primarySelectedEntityId);
    }

    public void setSelectedEntity(Entity entity) {
        setSelectedEntityId(entity != null ? entity.getId() : null);
    }

    public void setSelectedEntityId(UUID entityId) {
        if (entityId == null) {
            clearSelection();
            return;
        }
        setSelectedEntityIds(java.util.Collections.singleton(entityId), entityId);
    }

    public java.util.Set<UUID> getSelectedEntityIds() {
        return java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(selectedEntityIds));
    }

    public boolean isSelected(UUID entityId) {
        return entityId != null && selectedEntityIds.contains(entityId);
    }

    public boolean addSelectedEntityId(UUID entityId) {
        if (entityId == null || activeScene == null || activeScene.findEntityById(entityId) == null) {
            return false;
        }
        if (selectedEntityIds.contains(entityId)) {
            return false;
        }
        java.util.Set<UUID> prev = new java.util.LinkedHashSet<>(selectedEntityIds);
        UUID prevPrimary = primarySelectedEntityId;

        selectedEntityIds.add(entityId);
        if (primarySelectedEntityId == null) {
            primarySelectedEntityId = entityId;
        }

        notifySelectionChanged(prev, prevPrimary);
        return true;
    }

    public boolean removeSelectedEntityId(UUID entityId) {
        if (entityId == null || !selectedEntityIds.contains(entityId)) {
            return false;
        }
        java.util.Set<UUID> prev = new java.util.LinkedHashSet<>(selectedEntityIds);
        UUID prevPrimary = primarySelectedEntityId;

        selectedEntityIds.remove(entityId);
        if (Objects.equals(primarySelectedEntityId, entityId)) {
            primarySelectedEntityId = selectedEntityIds.isEmpty() ? null : selectedEntityIds.iterator().next();
        }

        notifySelectionChanged(prev, prevPrimary);
        return true;
    }

    public void setSelectedEntityIds(java.util.Collection<UUID> entityIds, UUID primaryId) {
        java.util.Set<UUID> validated = new java.util.LinkedHashSet<>();
        if (entityIds != null && activeScene != null) {
            for (UUID id : entityIds) {
                if (id != null && activeScene.findEntityById(id) != null) {
                    validated.add(id);
                }
            }
        }

        UUID newPrimary = null;
        if (primaryId != null && validated.contains(primaryId)) {
            newPrimary = primaryId;
        } else if (!validated.isEmpty()) {
            newPrimary = validated.iterator().next();
        }

        if (this.selectedEntityIds.equals(validated) && Objects.equals(this.primarySelectedEntityId, newPrimary)) {
            return;
        }

        java.util.Set<UUID> prev = new java.util.LinkedHashSet<>(this.selectedEntityIds);
        UUID prevPrimary = this.primarySelectedEntityId;

        this.selectedEntityIds.clear();
        this.selectedEntityIds.addAll(validated);
        this.primarySelectedEntityId = newPrimary;

        notifySelectionChanged(prev, prevPrimary);
    }

    private void notifySelectionChanged(java.util.Set<UUID> prevSelection, UUID prevPrimary) {
        java.util.Set<UUID> unmodifiableCurrent = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(selectedEntityIds));
        java.util.Set<UUID> unmodifiablePrev = java.util.Collections.unmodifiableSet(prevSelection);
        for (Listener l : listeners) {
            if (!Objects.equals(prevPrimary, this.primarySelectedEntityId)) {
                l.onSelectionChanged(prevPrimary, this.primarySelectedEntityId);
            }
            l.onMultiSelectionChanged(unmodifiablePrev, unmodifiableCurrent, this.primarySelectedEntityId);
        }
    }

    public void clearSelection() {
        if (selectedEntityIds.isEmpty() && primarySelectedEntityId == null) {
            return;
        }
        java.util.Set<UUID> prev = new java.util.LinkedHashSet<>(selectedEntityIds);
        UUID prevPrimary = primarySelectedEntityId;

        selectedEntityIds.clear();
        primarySelectedEntityId = null;

        notifySelectionChanged(prev, prevPrimary);
    }

    public ToolMode getToolMode() {
        return toolMode;
    }

    public void setToolMode(ToolMode newMode) {
        Objects.requireNonNull(newMode, "toolMode cannot be null");
        if (this.toolMode != newMode) {
            ToolMode old = this.toolMode;
            this.toolMode = newMode;
            for (Listener l : listeners) {
                l.onToolModeChanged(old, this.toolMode);
            }
        }
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isStatusError() {
        return statusIsError;
    }

    public void setStatusMessage(String message, boolean isError) {
        this.statusMessage = (message != null) ? message : "";
        this.statusIsError = isError;
        for (Listener l : listeners) {
            l.onStatusMessage(this.statusMessage, isError);
        }
    }

    private io.github.ldev22live.gdxunity.editor.core.play.PlayModeController playModeController;

    // --- Scene Editing Operations ---

    public void setPlayModeController(io.github.ldev22live.gdxunity.editor.core.play.PlayModeController playModeController) {
        this.playModeController = playModeController;
    }

    public io.github.ldev22live.gdxunity.editor.core.play.PlayModeController getPlayModeController() {
        return playModeController;
    }

    public boolean isPlayModeActive() {
        return playModeController != null && !playModeController.isStopped();
    }

    public Entity createEntity(String name) {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot create entity during Play/Pause mode", true);
            return null;
        }
        if (activeScene == null) {
            return null;
        }
        io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand cmd =
            new io.github.ldev22live.gdxunity.editor.core.command.CreateEntityCommand(this, name);
        boolean success = executeCommand(cmd);
        return success ? cmd.getCreatedEntity() : null;
    }

    public boolean deleteSelectedEntity() {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot delete entity during Play/Pause mode", true);
            return false;
        }
        Entity selected = getSelectedEntity();
        if (selected == null || activeScene == null) {
            return false;
        }
        io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand cmd =
            new io.github.ldev22live.gdxunity.editor.core.command.DeleteEntityCommand(this, selected.getId());
        return executeCommand(cmd);
    }

    public Entity duplicateSelectedEntity() {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot duplicate entity during Play/Pause mode", true);
            return null;
        }
        Entity selected = getSelectedEntity();
        if (selected == null || activeScene == null) {
            setStatusMessage("No GameObject selected to duplicate", true);
            return null;
        }
        io.github.ldev22live.gdxunity.editor.core.command.DuplicateEntityCommand cmd =
            new io.github.ldev22live.gdxunity.editor.core.command.DuplicateEntityCommand(this, selected.getId());
        boolean success = executeCommand(cmd);
        return success ? cmd.getDuplicatedEntity() : null;
    }

    public boolean reparentEntity(UUID entityId, UUID newParentId, boolean preserveWorldTransform) {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot reparent entity during Play/Pause mode", true);
            return false;
        }
        if (entityId == null || activeScene == null) {
            return false;
        }
        io.github.ldev22live.gdxunity.editor.core.command.ReparentEntityCommand cmd =
            new io.github.ldev22live.gdxunity.editor.core.command.ReparentEntityCommand(this, entityId, newParentId, preserveWorldTransform);
        return executeCommand(cmd);
    }

    public boolean reparentEntity(UUID entityId, UUID newParentId) {
        return reparentEntity(entityId, newParentId, false);
    }

    public void notifyHierarchyModified() {
        // Reconcile selection if selected entity was removed
        if (activeScene == null) {
            clearSelection();
        } else {
            java.util.List<UUID> toRemove = new java.util.ArrayList<>();
            for (UUID id : selectedEntityIds) {
                if (activeScene.findEntityById(id) == null) {
                    toRemove.add(id);
                }
            }
            if (!toRemove.isEmpty()) {
                java.util.Set<UUID> remaining = new java.util.LinkedHashSet<>(selectedEntityIds);
                remaining.removeAll(toRemove);
                UUID newPrimary = remaining.contains(primarySelectedEntityId) ? primarySelectedEntityId : (remaining.isEmpty() ? null : remaining.iterator().next());
                setSelectedEntityIds(remaining, newPrimary);
            }
        }
        for (Listener l : listeners) {
            l.onHierarchyModified();
        }
    }

    public void notifyEntityTransformModified(Entity entity) {
        if (entity != null) {
            for (Listener l : listeners) {
                l.onEntityTransformModified(entity);
            }
        }
    }

    // --- Scene Persistence ---

    public void newScene(String sceneName) {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot create new scene during Play/Pause mode", true);
            return;
        }
        Scene fresh = new Scene(sceneName != null ? sceneName : "New Scene");
        setActiveScene(fresh);
        setStatusMessage("Created new scene: " + fresh.getName(), false);
    }

    public void saveSceneToFile(File file) throws IOException {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot save scene during Play/Pause mode", true);
            throw new IllegalStateException("Cannot save scene while in Play/Pause mode");
        }
        Objects.requireNonNull(file, "file cannot be null");
        if (activeScene == null) {
            throw new IllegalStateException("No active scene to save");
        }

        String json = serializer.toJson(activeScene);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        Files.writeString(file.toPath(), json);
        setStatusMessage("Saved scene to: " + file.getName(), false);
    }

    public void loadSceneFromFile(File file) throws IOException {
        if (isPlayModeActive()) {
            setStatusMessage("Cannot load scene during Play/Pause mode", true);
            throw new IllegalStateException("Cannot load scene while in Play/Pause mode");
        }
        Objects.requireNonNull(file, "file cannot be null");
        if (!file.exists()) {
            setStatusMessage("File not found: " + file.getPath(), true);
            throw new IOException("File does not exist: " + file.getPath());
        }

        String json = Files.readString(file.toPath());
        Scene loaded = serializer.fromJson(json);
        setActiveScene(loaded);
        setStatusMessage("Loaded scene from: " + file.getName(), false);
    }

    public SceneSerializer getSerializer() {
        return serializer;
    }
}
