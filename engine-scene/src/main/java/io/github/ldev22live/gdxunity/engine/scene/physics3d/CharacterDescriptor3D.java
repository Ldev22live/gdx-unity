package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * Everything a {@link PhysicsBackend3D} needs to create one walking character, in meters, kilograms and radians (TD-049).
 * The capsule stands upright with its center at {@code position}.
 *
 * @param halfHeight  half of the capsule's cylinder part; the whole capsule is {@code 2 * (halfHeight + radius)} tall
 * @param slopeRadians steepest walkable slope in radians from flat
 * @param stepHeight  highest step climbed, in meters (0 turns it off)
 */
public record CharacterDescriptor3D(
    Vector3 position,
    float radius,
    float halfHeight,
    float slopeRadians,
    float stepHeight,
    float mass,
    int layer,
    int collisionMask) {

    public CharacterDescriptor3D {
        position = new Vector3(Objects.requireNonNull(position, "position cannot be null"));
        if (!(radius > 0f) || !(halfHeight > 0f)) {
            throw new IllegalArgumentException("A character needs a positive radius and height: " + radius + ", " + halfHeight);
        }
    }
}
