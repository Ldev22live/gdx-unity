package io.github.ldev22live.gdxunity.editor.core.workspace;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.Objects;

/**
 * The World screen's minimap (TD-107 point 3): a top view of the level drawn into a small widget. The map plane is the ground
 * ({@code x}, {@code z}) for 2.5D and 3D scenes and the screen plane ({@code x}, {@code y}) for 2D scenes. The level's bounds are
 * fitted into the widget keeping their aspect ratio and centred; a click maps back to a point on that plane. Pure: the desktop draws
 * the dots and frame and moves the camera.
 */
public final class MinimapMath {
    /** Space kept round the level's bounds, as a share of their larger side, so edge entities are not drawn on the border. */
    public static final float MARGIN = 0.05f;
    /** The smallest map extent, scene units, so a scene with one entity still has a usable scale. */
    public static final float MIN_EXTENT = 200f;

    private final boolean groundPlane;
    private final float minA;
    private final float minB;
    private final float extent;

    private MinimapMath(boolean groundPlane, float minA, float minB, float extent) {
        this.groundPlane = groundPlane;
        this.minA = minA;
        this.minB = minB;
        this.extent = extent;
    }

    /**
     * The map of {@code scene}: the bounds of its entities' world positions on the mode's plane, made square (the larger side),
     * widened by {@link #MARGIN} and at least {@link #MIN_EXTENT}.
     */
    public static MinimapMath of(Scene scene) {
        Objects.requireNonNull(scene, "scene cannot be null");
        boolean ground = scene.getGameMode() != GameMode.MODE_2D;
        float minA = Float.POSITIVE_INFINITY;
        float minB = Float.POSITIVE_INFINITY;
        float maxA = Float.NEGATIVE_INFINITY;
        float maxB = Float.NEGATIVE_INFINITY;
        for (Entity entity : scene.getAllEntities()) {
            Vector3 p = entity.getTransform().getWorldPosition();
            float a = p.x;
            float b = ground ? p.z : p.y;
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
        }
        if (minA > maxA) {
            minA = maxA = minB = maxB = 0f;
        }
        float extent = Math.max(MIN_EXTENT, Math.max(maxA - minA, maxB - minB) * (1f + 2f * MARGIN));
        float centreA = (minA + maxA) / 2f;
        float centreB = (minB + maxB) / 2f;
        return new MinimapMath(ground, centreA - extent / 2f, centreB - extent / 2f, extent);
    }

    /** True when the map is the ground plane (x, z), false for the 2D screen plane (x, y). */
    public boolean isGroundPlane() {
        return groundPlane;
    }

    /** The side of the square map area, scene units. */
    public float extent() {
        return extent;
    }

    /**
     * Where world point {@code world} falls in a {@code size} × {@code size} minimap, origin bottom-left, in {@code out}. On the
     * ground plane +z (towards the default camera) is drawn downwards, as a map seen from above.
     */
    public Vector2 toMinimap(Vector3 world, float size, Vector2 out) {
        float u = (world.x - minA) / extent;
        float v = ((groundPlane ? world.z : world.y) - minB) / extent;
        return out.set(u * size, (groundPlane ? 1f - v : v) * size);
    }

    /** The world point under minimap point ({@code x}, {@code y}) of a {@code size} minimap, on the plane at height {@code level}. */
    public Vector3 toWorld(float x, float y, float size, float level, Vector3 out) {
        float u = x / size;
        float v = groundPlane ? 1f - y / size : y / size;
        float a = minA + u * extent;
        float b = minB + v * extent;
        return groundPlane ? out.set(a, level, b) : out.set(a, b, level);
    }
}
