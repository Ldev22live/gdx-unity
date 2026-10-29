package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.util.List;
import java.util.Objects;

/**
 * Everything a {@link PhysicsBackend3D} needs to create one body, in meters and kilograms.
 *
 * @param sensor true for a trigger body: it reports overlaps and never blocks or is blocked
 */
public record BodyDescriptor3D(
    RigidBody3DComponent.MotionType motionType,
    Vector3 position,
    Quaternion rotation,
    List<ShapeDescriptor3D> shapes,
    boolean sensor,
    float mass,
    float linearDamping,
    float angularDamping,
    float gravityFactor,
    float friction,
    float restitution,
    int layer,
    int collisionMask) {

    public BodyDescriptor3D {
        Objects.requireNonNull(motionType, "motionType cannot be null");
        position = new Vector3(Objects.requireNonNull(position, "position cannot be null"));
        rotation = new Quaternion(Objects.requireNonNull(rotation, "rotation cannot be null"));
        shapes = List.copyOf(Objects.requireNonNull(shapes, "shapes cannot be null"));
        if (shapes.isEmpty()) {
            throw new IllegalArgumentException("A body needs at least one shape");
        }
    }
}
