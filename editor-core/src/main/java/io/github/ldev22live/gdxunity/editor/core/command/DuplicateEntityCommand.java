package io.github.ldev22live.gdxunity.editor.core.command;

import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;
import java.util.UUID;

/**
 * Command to duplicate an entity in the active scene with independent identity,
 * copied transform offset, and undo/redo support.
 */
public class DuplicateEntityCommand implements EditorCommand {
    private final EditorState editorState;
    private final UUID sourceEntityId;
    private Entity duplicatedEntity;
    private UUID duplicatedId;

    public DuplicateEntityCommand(EditorState editorState, UUID sourceEntityId) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.sourceEntityId = Objects.requireNonNull(sourceEntityId, "sourceEntityId cannot be null");
    }

    @Override
    public boolean execute() {
        if (editorState.isPlayModeActive()) {
            editorState.setStatusMessage("Cannot duplicate entity during Play/Pause mode", true);
            return false;
        }
        Scene scene = editorState.getActiveScene();
        if (scene == null) return false;

        Entity source = scene.findEntityById(sourceEntityId);
        if (source == null && duplicatedEntity == null) return false;

        if (duplicatedEntity == null) {
            duplicatedEntity = scene.createEntity(source.getName() + " (Copy)");
            duplicatedId = duplicatedEntity.getId();

            Vector3 pos = source.getTransform().getLocalPosition();
            duplicatedEntity.getTransform().setLocalPosition(pos.x + 20f, pos.y + 20f, pos.z);
            duplicatedEntity.getTransform().setLocalRotation(source.getTransform().getLocalRotation());
            duplicatedEntity.getTransform().setLocalScale(source.getTransform().getLocalScale());

            // Clone components where supported
            for (Component comp : source.getAllComponents()) {
                if (comp instanceof io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent shape) {
                    duplicatedEntity.addComponent(new io.github.ldev22live.gdxunity.engine.render.components.ShapeRendererComponent(
                        shape.getWidth(), shape.getHeight(), shape.getColor()
                    ));
                } else if (comp instanceof io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent sprite) {
                    duplicatedEntity.addComponent(new io.github.ldev22live.gdxunity.engine.render.components.SpriteRendererComponent(
                        sprite.getAssetReference(), sprite.getWidth(), sprite.getHeight()
                    ));
                } else if (comp instanceof io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent scriptBinding) {
                    io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent clone =
                        new io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent(
                            scriptBinding.getScriptPath(), scriptBinding.getScriptClassName(), scriptBinding.getLanguage()
                        );
                    clone.setEnabled(scriptBinding.isEnabled());
                    duplicatedEntity.addComponent(clone);
                } else if (comp instanceof io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent rb) {
                    io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent clone =
                        new io.github.ldev22live.gdxunity.engine.scene.physics.RigidBody2DComponent(rb.getBodyType());
                    clone.setGravityScale(rb.getGravityScale());
                    clone.setLinearDamping(rb.getLinearDamping());
                    clone.setAngularDamping(rb.getAngularDamping());
                    clone.setFixedRotation(rb.isFixedRotation());
                    clone.setBullet(rb.isBullet());
                    clone.setInitialLinearVelocity(rb.getInitialLinearVelocity());
                    clone.setInitialAngularVelocity(rb.getInitialAngularVelocity());
                    clone.setEnabled(rb.isEnabled());
                    duplicatedEntity.addComponent(clone);
                } else if (comp instanceof io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent box) {
                    io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent clone =
                        new io.github.ldev22live.gdxunity.engine.scene.physics.BoxCollider2DComponent(box.getWidth(), box.getHeight(), box.getOffset());
                    clone.setDensity(box.getDensity());
                    clone.setFriction(box.getFriction());
                    clone.setRestitution(box.getRestitution());
                    clone.setSensor(box.isSensor());
                    clone.setCategoryBits(box.getCategoryBits());
                    clone.setMaskBits(box.getMaskBits());
                    clone.setEnabled(box.isEnabled());
                    duplicatedEntity.addComponent(clone);
                } else if (comp instanceof io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent circle) {
                    io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent clone =
                        new io.github.ldev22live.gdxunity.engine.scene.physics.CircleCollider2DComponent(circle.getRadius(), circle.getOffset());
                    clone.setDensity(circle.getDensity());
                    clone.setFriction(circle.getFriction());
                    clone.setRestitution(circle.getRestitution());
                    clone.setSensor(circle.isSensor());
                    clone.setCategoryBits(circle.getCategoryBits());
                    clone.setMaskBits(circle.getMaskBits());
                    clone.setEnabled(circle.isEnabled());
                    duplicatedEntity.addComponent(clone);
                }
            }
        } else {
            // Redo: re-add existing entity instance into scene
            scene.addEntity(duplicatedEntity);
        }

        editorState.setSelectedEntity(duplicatedEntity);
        editorState.notifyHierarchyModified();
        editorState.setStatusMessage("Duplicated: " + duplicatedEntity.getName(), false);
        return true;
    }

    @Override
    public boolean undo() {
        if (duplicatedEntity == null) return false;
        Scene scene = editorState.getActiveScene();
        if (scene == null) return false;

        if (Objects.equals(editorState.getSelectedEntityId(), duplicatedId)) {
            editorState.clearSelection();
        }
        boolean removed = scene.removeEntity(duplicatedEntity, false);
        if (removed) {
            editorState.notifyHierarchyModified();
            editorState.setStatusMessage("Undid duplication of: " + duplicatedEntity.getName(), false);
        }
        return removed;
    }

    @Override
    public String getDescription() {
        return "Duplicate Entity";
    }

    public Entity getDuplicatedEntity() {
        return duplicatedEntity;
    }
}
