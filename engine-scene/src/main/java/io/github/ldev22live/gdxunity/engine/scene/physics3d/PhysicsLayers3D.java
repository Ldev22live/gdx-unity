package io.github.ldev22live.gdxunity.engine.scene.physics3d;

/**
 * Collision layers for 3D physics: each body sits on one layer and has a mask of the layers it collides
 * with. Two bodies collide only when each one's mask contains the other's layer.
 */
public final class PhysicsLayers3D {
    /** Number of layers; masks are {@code LAYER_COUNT} bits wide. */
    public static final int LAYER_COUNT = 16;
    public static final int DEFAULT_LAYER = 0;
    /** Mask with every layer set. */
    public static final int ALL_LAYERS = (1 << LAYER_COUNT) - 1;

    private PhysicsLayers3D() {}

    public static boolean collide(int layerA, int maskA, int layerB, int maskB) {
        return (maskA & (1 << layerB)) != 0 && (maskB & (1 << layerA)) != 0;
    }

    static void requireValidLayer(int layer) {
        if (layer < 0 || layer >= LAYER_COUNT) {
            throw new IllegalArgumentException("layer must be between 0 and " + (LAYER_COUNT - 1) + ": " + layer);
        }
    }
}
