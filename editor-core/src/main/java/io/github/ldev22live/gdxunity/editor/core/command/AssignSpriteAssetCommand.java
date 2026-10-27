package io.github.ldev22live.gdxunity.editor.core.command;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to assign or clear a sprite asset reference on an entity's SpriteRendererComponent.
 */
public class AssignSpriteAssetCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID entityId;
    private final AssetReference previousAsset;
    private final AssetReference newAsset;

    public AssignSpriteAssetCommand(EditorState editorState, UUID entityId, AssetReference previousAsset, AssetReference newAsset) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId cannot be null");
        this.previousAsset = previousAsset;
        this.newAsset = newAsset;
    }

    @Override
    public boolean execute() {
        if (Objects.equals(previousAsset, newAsset)) {
            return false;
        }
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        SpriteRendererComponent sprite = entity.getComponent(SpriteRendererComponent.class);
        if (sprite == null) {
            return false;
        }
        sprite.setAssetReference(newAsset);
        sprite.setTexture(null); // allow lazy resolution via AssetService
        editorState.notifyHierarchyModified();
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Assigned asset: " + (newAsset != null ? newAsset.getPath() : "[None]"), false);
        return true;
    }

    @Override
    public boolean undo() {
        Entity entity = findTarget();
        if (entity == null) {
            return false;
        }
        SpriteRendererComponent sprite = entity.getComponent(SpriteRendererComponent.class);
        if (sprite == null) {
            return false;
        }
        sprite.setAssetReference(previousAsset);
        sprite.setTexture(null);
        editorState.notifyHierarchyModified();
        editorState.notifyEntityTransformModified(entity);
        editorState.setStatusMessage("Undid asset assignment to: " + (previousAsset != null ? previousAsset.getPath() : "[None]"), false);
        return true;
    }

    @Override
    public String getDescription() {
        return (newAsset != null ? "Assign Asset '" + newAsset.getFileName() + "'" : "Clear Sprite Asset");
    }

    private Entity findTarget() {
        if (editorState.getActiveScene() == null) return null;
        return editorState.getActiveScene().findEntityById(entityId);
    }
}
