package io.github.ldev22live.gdxunity.engine.scene.physics3d;

/**
 * Scene units versus physics meters. The 3D editor uses pixel-sized units (a default box is 100 units), while
 * physics engines are tuned for objects around a meter, so 100 scene units are one meter, as in
 * {@code PhysicsUnits2D}.
 */
public final class PhysicsUnits3D {
    public static final float UNITS_PER_METER = 100f;
    public static final float METERS_PER_UNIT = 1f / UNITS_PER_METER;

    private PhysicsUnits3D() {}

    public static float toMeters(float sceneUnits) {
        return sceneUnits * METERS_PER_UNIT;
    }

    public static float toSceneUnits(float meters) {
        return meters * UNITS_PER_METER;
    }
}
