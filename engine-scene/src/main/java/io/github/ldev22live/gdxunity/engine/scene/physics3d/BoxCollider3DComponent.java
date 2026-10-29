package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/** Box collision shape; {@link #getSize()} is the full edge length on each local axis. */
public class BoxCollider3DComponent extends Collider3DComponent {
    /** Edge length of a new box collider, matching the default box primitive. */
    public static final float DEFAULT_SIZE = 100f;

    private final Vector3 size = new Vector3(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE);

    public BoxCollider3DComponent() {
    }

    public BoxCollider3DComponent(Vector3 size) {
        setSize(size);
    }

    public Vector3 getSize() {
        return new Vector3(size);
    }

    public void setSize(Vector3 size) {
        Objects.requireNonNull(size, "size cannot be null");
        requirePositive(size.x, "size.x");
        requirePositive(size.y, "size.y");
        requirePositive(size.z, "size.z");
        this.size.set(size);
    }
}
