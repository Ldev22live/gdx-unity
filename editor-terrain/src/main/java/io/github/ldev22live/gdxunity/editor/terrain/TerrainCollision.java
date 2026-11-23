package io.github.ldev22live.gdxunity.editor.terrain;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.AddComponentCommand;
import io.github.ldev22live.gdxunity.editor.core.command.CompoundCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.editor.core.command.ModifyComponentPropertyCommand;
import io.github.ldev22live.gdxunity.engine.render.components.TerrainComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.CharacterController3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.RigidBody3DComponent;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.TerrainCollider3DComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * New terrains are walkable without a setup step (TD-087): giving an entity a new terrain also gives it a Terrain Collider 3D, in the
 * same undo step, unless it already has one or cannot have one (a character, or a body that moves).
 */
public final class TerrainCollision {
    private TerrainCollision() { }

    /** Whether a Terrain Collider 3D should be added to {@code entity}: it has none, and it is not a character or a moving body. */
    public static boolean needsCollider(Entity entity) {
        Objects.requireNonNull(entity, "entity cannot be null");
        if (entity.getComponent(TerrainCollider3DComponent.class) != null || entity.getComponent(CharacterController3DComponent.class) != null) {
            return false;
        }
        RigidBody3DComponent body = entity.getComponent(RigidBody3DComponent.class);
        return body == null || !body.isEnabled() || body.getMotionType() == RigidBody3DComponent.MotionType.STATIC;
    }

    /**
     * Points {@code component} at the terrain in {@code path} and, when {@link #needsCollider} says so, adds a Terrain Collider 3D:
     * one undo step named "Change Terrain".
     *
     * @return false when nothing changed (the same path) or the editor refused the change, for example during Play
     */
    public static boolean assignTerrain(EditorState editorState, Entity entity, TerrainComponent component, String path) {
        Objects.requireNonNull(editorState, "editorState cannot be null");
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(component, "component cannot be null");
        Objects.requireNonNull(path, "path cannot be null");
        if (path.equals(component.getTerrainPath())) {
            return false;
        }
        List<EditorCommand> steps = new ArrayList<>();
        steps.add(new ModifyComponentPropertyCommand<TerrainComponent, String>(editorState, entity.getId(), component,
            component.getTerrainPath(), path, component::setTerrainPath, "Change Terrain"));
        if (needsCollider(entity)) {
            steps.add(new AddComponentCommand(editorState, entity.getId(), new TerrainCollider3DComponent()));
        }
        return editorState.executeCommand(new CompoundCommand("Change Terrain", steps));
    }
}
