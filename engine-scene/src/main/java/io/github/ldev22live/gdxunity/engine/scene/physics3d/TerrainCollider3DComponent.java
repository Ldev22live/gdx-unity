package io.github.ldev22live.gdxunity.engine.scene.physics3d;

/**
 * Makes the entity's terrain solid (TD-087): characters stand, walk, jump and slide on it, bodies rest on it and raycasts hit it.
 * The heights come from the entity's Terrain component when Play starts, through the scene's {@link HeightFieldSource}, and are
 * read again whenever the terrain changes while physics runs. The body is static: a moving Rigid Body 3D on the same entity is
 * refused. A ground is never a sensor, so this collider cannot be a trigger.
 */
public class TerrainCollider3DComponent extends Collider3DComponent {
    /**
     * @throws IllegalArgumentException when {@code trigger} is true: a terrain is solid ground
     */
    @Override
    public void setTrigger(boolean trigger) {
        if (trigger) {
            throw new IllegalArgumentException("A Terrain Collider 3D cannot be a trigger: the ground is solid. Use a Box Collider 3D "
                + "trigger for an area on the ground.");
        }
        super.setTrigger(false);
    }
}
