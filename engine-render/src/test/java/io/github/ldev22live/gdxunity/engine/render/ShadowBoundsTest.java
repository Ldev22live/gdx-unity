package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShadowBoundsTest {
    private static final float DISTANCE = 3000f;
    private static final int MAP_SIZE = 2048;
    private static final float TEXEL = DISTANCE / MAP_SIZE;
    private static final Vector3 LIGHT = new Vector3(-0.4f, -1f, -0.6f);

    @BeforeAll
    static void init() {
        GdxTestHelper.initNatives();
    }

    @Test
    void shouldCenterVolumeHalfTheShadowDistanceInFrontOfTheCamera() {
        ShadowBounds bounds = new ShadowBounds().fit(new Vector3(100f, 200f, 300f), new Vector3(0f, 0f, -2f), LIGHT,
            DISTANCE, MAP_SIZE);

        Vector3 expected = new Vector3(100f, 200f, 300f - DISTANCE / 2f);
        // Snapping moves the center by less than one texel along each of the two axes across the light
        assertTrue(bounds.getCenter().dst(expected) < TEXEL * 1.5f, () -> "center " + bounds.getCenter());
        assertEquals(DISTANCE, bounds.getSize());
        assertEquals(DISTANCE * ShadowBounds.DEPTH_FACTOR, bounds.getDepth());
    }

    @Test
    void shouldSnapCenterToWholeTexelsAcrossTheLight() {
        ShadowBounds bounds = new ShadowBounds().fit(new Vector3(123.4f, 56.7f, -89.1f), new Vector3(1f, -0.5f, 0.3f),
            LIGHT, DISTANCE, MAP_SIZE);

        assertWholeTexels(bounds.getCenter().dot(bounds.getRight()));
        assertWholeTexels(bounds.getCenter().dot(bounds.getUp()));
    }

    @Test
    void shouldKeepTheSameTexelGridWhenCameraMovesAlongTheLight() {
        Vector3 position = new Vector3(321f, 150f, 87f);
        Vector3 direction = new Vector3(0.3f, -0.4f, -1f);
        ShadowBounds before = new ShadowBounds().fit(position, direction, LIGHT, DISTANCE, MAP_SIZE);
        float a = before.getCenter().dot(before.getRight());
        float b = before.getCenter().dot(before.getUp());

        Vector3 moved = new Vector3(LIGHT).nor().scl(500f).add(position);
        ShadowBounds after = new ShadowBounds().fit(moved, direction, LIGHT, DISTANCE, MAP_SIZE);

        assertEquals(a, after.getCenter().dot(after.getRight()), 1e-2f);
        assertEquals(b, after.getCenter().dot(after.getUp()), 1e-2f);
    }

    @Test
    void shouldContainEveryPointOnTheViewAxisWithinTheShadowDistance() {
        Vector3 position = new Vector3(-250f, 400f, 900f);
        Vector3 direction = new Vector3(0.2f, -0.5f, -1f).nor();
        ShadowBounds bounds = new ShadowBounds().fit(position, direction, LIGHT, DISTANCE, MAP_SIZE);
        Vector3 forward = new Vector3(LIGHT).nor();

        for (float d = 0f; d <= DISTANCE; d += DISTANCE / 10f) {
            Vector3 offset = new Vector3(direction).scl(d).add(position).sub(bounds.getCenter());
            assertTrue(Math.abs(offset.dot(bounds.getRight())) <= bounds.getSize() / 2f + TEXEL, "right at " + d);
            assertTrue(Math.abs(offset.dot(bounds.getUp())) <= bounds.getSize() / 2f + TEXEL, "up at " + d);
            assertTrue(Math.abs(offset.dot(forward)) <= bounds.getDepth(), "depth at " + d);
        }
    }

    @Test
    void shouldBuildAnOrthonormalFrameForAStraightDownLight() {
        ShadowBounds bounds = new ShadowBounds().fit(new Vector3(), new Vector3(0f, 0f, -1f), new Vector3(0f, -1f, 0f),
            DISTANCE, MAP_SIZE);

        assertEquals(1f, bounds.getRight().len(), 1e-5f);
        assertEquals(1f, bounds.getUp().len(), 1e-5f);
        assertEquals(0f, bounds.getRight().dot(bounds.getUp()), 1e-5f);
        assertFalse(Float.isNaN(bounds.getCenter().x));
    }

    @Test
    void shouldRejectInvalidArguments() {
        ShadowBounds bounds = new ShadowBounds();
        Vector3 origin = new Vector3();
        Vector3 forward = new Vector3(0f, 0f, -1f);

        assertThrows(IllegalArgumentException.class, () -> bounds.fit(origin, forward, LIGHT, 0f, MAP_SIZE));
        assertThrows(IllegalArgumentException.class, () -> bounds.fit(origin, forward, LIGHT, DISTANCE, 0));
        assertThrows(IllegalArgumentException.class, () -> bounds.fit(origin, forward, new Vector3(), DISTANCE, MAP_SIZE));
        assertThrows(NullPointerException.class, () -> bounds.fit(null, forward, LIGHT, DISTANCE, MAP_SIZE));
    }

    private static void assertWholeTexels(float coordinate) {
        float texels = coordinate / TEXEL;
        assertEquals(Math.round(texels), texels, 1e-2f, "not on the texel grid: " + coordinate);
    }
}
