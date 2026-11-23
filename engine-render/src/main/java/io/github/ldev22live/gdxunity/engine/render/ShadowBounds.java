package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * Places a directional light's orthographic shadow volume around the part of the scene the camera looks at.
 * Pure math, no graphics context.
 *
 * <p>The volume is a box of {@code shadowDistance} × {@code shadowDistance} across the light, centered half the
 * shadow distance in front of the camera, so it covers a sphere of radius {@code shadowDistance / 2} there. Its
 * center is snapped to whole shadow-map texels in the light's frame: while the camera moves, every texel keeps
 * covering the same patch of the scene, which stops shadow edges from crawling ("shimmering").
 *
 * <p>Along the light the volume extends {@link #DEPTH_FACTOR} times the shadow distance behind and in front of
 * the center, so tall objects between the sun and the visible area still cast into it.
 */
public final class ShadowBounds {
    /** Depth of the volume on each side of the center, as a multiple of the shadow distance. */
    public static final float DEPTH_FACTOR = 1f;
    /** Above this |dot(light, Y)| the light counts as vertical and Z replaces Y as the reference axis. */
    private static final float VERTICAL_LIGHT_DOT = 0.999f;

    private final Vector3 center = new Vector3();
    private final Vector3 right = new Vector3();
    private final Vector3 up = new Vector3();
    private final Vector3 forward = new Vector3();
    private float size;
    private float depth;

    /**
     * Fits the volume. Reuses this object's vectors; nothing is allocated.
     *
     * @param cameraPosition  camera position in world units
     * @param cameraDirection camera view direction (need not be normalized)
     * @param lightDirection  direction the light travels (need not be normalized)
     * @param shadowDistance  width of the shadowed area in world units, > 0
     * @param shadowMapSize   shadow map width in texels, > 0
     * @return this, for chaining
     */
    public ShadowBounds fit(Vector3 cameraPosition, Vector3 cameraDirection, Vector3 lightDirection,
                            float shadowDistance, int shadowMapSize) {
        Objects.requireNonNull(cameraPosition, "cameraPosition cannot be null");
        Objects.requireNonNull(cameraDirection, "cameraDirection cannot be null");
        Objects.requireNonNull(lightDirection, "lightDirection cannot be null");
        if (!(shadowDistance > 0f) || Float.isInfinite(shadowDistance)) {
            throw new IllegalArgumentException("shadowDistance must be a finite value > 0: " + shadowDistance);
        }
        if (shadowMapSize <= 0) {
            throw new IllegalArgumentException("shadowMapSize must be > 0: " + shadowMapSize);
        }
        if (lightDirection.isZero() || cameraDirection.isZero()) {
            throw new IllegalArgumentException("light and camera directions must not be zero");
        }

        forward.set(lightDirection).nor();
        if (Math.abs(forward.y) > VERTICAL_LIGHT_DOT) {
            right.set(forward).crs(Vector3.Z).nor();
        } else {
            right.set(forward).crs(Vector3.Y).nor();
        }
        up.set(right).crs(forward).nor();

        // Half the shadow distance in front of the camera
        float half = shadowDistance / 2f;
        float cx = cameraPosition.x, cy = cameraPosition.y, cz = cameraPosition.z;
        float len = cameraDirection.len();
        cx += cameraDirection.x / len * half;
        cy += cameraDirection.y / len * half;
        cz += cameraDirection.z / len * half;

        // Snap the across-light coordinates to whole texels
        float texel = shadowDistance / shadowMapSize;
        float a = snap(right.x * cx + right.y * cy + right.z * cz, texel);
        float b = snap(up.x * cx + up.y * cy + up.z * cz, texel);
        float c = forward.x * cx + forward.y * cy + forward.z * cz;
        center.set(
            right.x * a + up.x * b + forward.x * c,
            right.y * a + up.y * b + forward.y * c,
            right.z * a + up.z * b + forward.z * c);

        size = shadowDistance;
        depth = shadowDistance * DEPTH_FACTOR;
        return this;
    }

    private static float snap(float value, float step) {
        // Math.floor, not MathUtils.floor: the latter is only exact above -2^14
        return (float) Math.floor(value / step) * step;
    }

    /** Center of the volume (owned by this object; copy it to keep it). */
    public Vector3 getCenter() {
        return center;
    }

    /** Width and height of the volume across the light. */
    public float getSize() {
        return size;
    }

    /** Distance from the center to the near and far planes along the light. */
    public float getDepth() {
        return depth;
    }

    /** Unit axis across the light used for snapping (owned by this object). */
    public Vector3 getRight() {
        return right;
    }

    /** Second unit axis across the light used for snapping (owned by this object). */
    public Vector3 getUp() {
        return up;
    }
}
