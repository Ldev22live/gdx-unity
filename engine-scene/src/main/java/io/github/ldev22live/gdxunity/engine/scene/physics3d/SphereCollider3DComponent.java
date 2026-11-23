package io.github.ldev22live.gdxunity.engine.scene.physics3d;

/** Sphere collision shape. Under non-uniform scale the largest axis scale is used. */
public class SphereCollider3DComponent extends Collider3DComponent {
    /** Radius of a new sphere collider, matching the default sphere primitive. */
    public static final float DEFAULT_RADIUS = 50f;

    private float radius = DEFAULT_RADIUS;

    public SphereCollider3DComponent() {
    }

    public SphereCollider3DComponent(float radius) {
        setRadius(radius);
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = requirePositive(radius, "radius");
    }
}
