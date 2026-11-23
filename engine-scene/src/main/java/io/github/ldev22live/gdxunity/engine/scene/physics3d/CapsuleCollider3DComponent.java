package io.github.ldev22live.gdxunity.engine.scene.physics3d;

/**
 * Capsule collision shape standing on the local Y axis: a cylinder of {@link #getHeight()} capped by two
 * half-spheres of {@link #getRadius()}, so its total height is {@code height + 2 * radius}. The usual shape
 * for characters.
 */
public class CapsuleCollider3DComponent extends Collider3DComponent {
    public static final float DEFAULT_RADIUS = 25f;
    public static final float DEFAULT_HEIGHT = 100f;

    private float radius = DEFAULT_RADIUS;
    private float height = DEFAULT_HEIGHT;

    public CapsuleCollider3DComponent() {
    }

    public CapsuleCollider3DComponent(float radius, float height) {
        setRadius(radius);
        setHeight(height);
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = requirePositive(radius, "radius");
    }

    /** Height of the cylindrical middle part. */
    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = requirePositive(height, "height");
    }
}
