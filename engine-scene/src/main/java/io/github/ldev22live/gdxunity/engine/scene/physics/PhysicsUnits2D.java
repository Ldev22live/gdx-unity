package io.github.ldev22live.gdxunity.engine.scene.physics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

/**
 * Single source of truth for converting between GdxUnity scene coordinates (pixels)
 * and Box2D physics coordinates (meters).
 */
public final class PhysicsUnits2D {
    /** Pixels Per Unit conversion factor (100 pixels = 1 meter). */
    public static final float PIXELS_PER_METER = 100.0f;
    public static final float METERS_PER_PIXEL = 1.0f / PIXELS_PER_METER;

    private PhysicsUnits2D() {
    }

    public static float toPhysicsLength(float pixels) {
        validateFinite(pixels, "pixels");
        return pixels * METERS_PER_PIXEL;
    }

    public static float toSceneLength(float meters) {
        validateFinite(meters, "meters");
        return meters * PIXELS_PER_METER;
    }

    public static Vector2 toPhysicsPosition(float sceneX, float sceneY) {
        validateFinite(sceneX, "sceneX");
        validateFinite(sceneY, "sceneY");
        return new Vector2(sceneX * METERS_PER_PIXEL, sceneY * METERS_PER_PIXEL);
    }

    public static Vector2 toPhysicsPosition(Vector2 scenePos) {
        if (scenePos == null) return new Vector2();
        return toPhysicsPosition(scenePos.x, scenePos.y);
    }

    public static Vector2 toPhysicsPosition(Vector3 scenePos) {
        if (scenePos == null) return new Vector2();
        return toPhysicsPosition(scenePos.x, scenePos.y);
    }

    public static Vector2 toScenePosition(float physicsX, float physicsY) {
        validateFinite(physicsX, "physicsX");
        validateFinite(physicsY, "physicsY");
        return new Vector2(physicsX * PIXELS_PER_METER, physicsY * PIXELS_PER_METER);
    }

    public static Vector2 toScenePosition(Vector2 physicsPos) {
        if (physicsPos == null) return new Vector2();
        return toScenePosition(physicsPos.x, physicsPos.y);
    }

    public static float toPhysicsAngle(float degrees) {
        validateFinite(degrees, "degrees");
        return (float) Math.toRadians(degrees);
    }

    public static float toSceneAngle(float radians) {
        validateFinite(radians, "radians");
        return (float) Math.toDegrees(radians);
    }

    private static void validateFinite(float val, String name) {
        if (!Float.isFinite(val)) {
            throw new IllegalArgumentException(name + " must be finite: " + val);
        }
    }
}
