package io.github.ldev22live.gdxunity.engine.scene.physics3d;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigidBody3DComponentTest {

    /** Roadmap M1.2: what a graph asks before it pushes a body, the same test the sandbox script makes. */
    @Test
    void shouldBeDynamicOnlyWhenEnabledAndOfTheDynamicMotionType() {
        RigidBody3DComponent body = new RigidBody3DComponent();
        body.setMotionType(RigidBody3DComponent.MotionType.DYNAMIC);
        assertTrue(body.isDynamic());

        body.setEnabled(false);
        assertFalse(body.isDynamic(), "a switched-off body is not pushed");
        body.setEnabled(true);
        body.setMotionType(RigidBody3DComponent.MotionType.STATIC);
        assertFalse(body.isDynamic());
        body.setMotionType(RigidBody3DComponent.MotionType.KINEMATIC);
        assertFalse(body.isDynamic());
    }
}
