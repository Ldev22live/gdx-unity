package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * A joint handed to a {@link PhysicsBackend3D} (TD-051): meters and radians, everything in world space at the moment of
 * creation. The pose the two bodies have then is the joint's rest pose.
 *
 * @param type            what the joint allows
 * @param bodyA           the first body's handle, a dynamic or kinematic or static body (never a character)
 * @param bodyB           the second body's handle, or {@link #WORLD} to fix the joint to the world
 * @param anchor          the anchor point on body A, world space (for BALL, FIXED, HINGE and SLIDER it is the joint's one point)
 * @param anchorB         DISTANCE only: the anchor point on body B, world space; the other types ignore it
 * @param axis            hinge or slider axis, world space, unit length (ignored by the other types)
 * @param collideConnected whether the two bodies still collide with each other
 * @param limited         whether {@code lower} and {@code upper} apply; for DISTANCE off means "keep the starting distance"
 * @param lower           hinge angle in radians, slider position or anchor distance in meters
 * @param upper           see {@code lower}
 * @param motorMode       what the motor does at the start
 * @param motorTarget     radians per second or radians (hinge), meters per second or meters (slider)
 * @param motorMaxForce   newton meters (hinge) or newtons (slider)
 */
public record JointDescriptor3D(Joint3DComponent.Type type, int bodyA, int bodyB, Vector3 anchor, Vector3 anchorB, Vector3 axis,
                                boolean collideConnected, boolean limited, float lower, float upper,
                                Joint3DComponent.MotorMode motorMode, float motorTarget, float motorMaxForce) {

    /** {@code bodyB} of a joint that holds {@code bodyA} against the world. */
    public static final int WORLD = Integer.MIN_VALUE;

    public JointDescriptor3D {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(motorMode, "motorMode cannot be null");
        anchor = new Vector3(Objects.requireNonNull(anchor, "anchor cannot be null"));
        anchorB = new Vector3(Objects.requireNonNull(anchorB, "anchorB cannot be null"));
        axis = new Vector3(Objects.requireNonNull(axis, "axis cannot be null"));
    }
}
