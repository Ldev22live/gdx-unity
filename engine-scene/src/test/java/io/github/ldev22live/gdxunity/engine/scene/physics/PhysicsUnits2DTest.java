package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsUnits2DTest {

    @Test
    void shouldConvertLengthsAccurately() {
        assertEquals(0.0f, PhysicsUnits2D.toPhysicsLength(0.0f), 0.0001f);
        assertEquals(1.0f, PhysicsUnits2D.toPhysicsLength(100.0f), 0.0001f);
        assertEquals(2.5f, PhysicsUnits2D.toPhysicsLength(250.0f), 0.0001f);
        assertEquals(-0.5f, PhysicsUnits2D.toPhysicsLength(-50.0f), 0.0001f);

        assertEquals(0.0f, PhysicsUnits2D.toSceneLength(0.0f), 0.0001f);
        assertEquals(100.0f, PhysicsUnits2D.toSceneLength(1.0f), 0.0001f);
        assertEquals(250.0f, PhysicsUnits2D.toSceneLength(2.5f), 0.0001f);
    }

    @Test
    void shouldConvertPositionsAccurately() {
        Vector2 phys = PhysicsUnits2D.toPhysicsPosition(150.0f, -200.0f);
        assertEquals(1.5f, phys.x, 0.0001f);
        assertEquals(-2.0f, phys.y, 0.0001f);

        Vector2 scene = PhysicsUnits2D.toScenePosition(phys);
        assertEquals(150.0f, scene.x, 0.0001f);
        assertEquals(-200.0f, scene.y, 0.0001f);

        Vector3 scene3 = new Vector3(300.0f, 400.0f, 0.0f);
        Vector2 physFrom3 = PhysicsUnits2D.toPhysicsPosition(scene3);
        assertEquals(3.0f, physFrom3.x, 0.0001f);
        assertEquals(4.0f, physFrom3.y, 0.0001f);
    }

    @Test
    void shouldConvertAnglesBetweenDegreesAndRadians() {
        assertEquals(0.0f, PhysicsUnits2D.toPhysicsAngle(0.0f), 0.0001f);
        assertEquals((float) Math.PI, PhysicsUnits2D.toPhysicsAngle(180.0f), 0.0001f);
        assertEquals((float) (Math.PI / 2.0), PhysicsUnits2D.toPhysicsAngle(90.0f), 0.0001f);

        assertEquals(0.0f, PhysicsUnits2D.toSceneAngle(0.0f), 0.0001f);
        assertEquals(180.0f, PhysicsUnits2D.toSceneAngle((float) Math.PI), 0.0001f);
        assertEquals(90.0f, PhysicsUnits2D.toSceneAngle((float) (Math.PI / 2.0)), 0.0001f);
    }

    @Test
    void shouldRejectNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> PhysicsUnits2D.toPhysicsLength(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> PhysicsUnits2D.toPhysicsLength(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> PhysicsUnits2D.toScenePosition(Float.NaN, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> PhysicsUnits2D.toPhysicsAngle(Float.NEGATIVE_INFINITY));
    }
}
