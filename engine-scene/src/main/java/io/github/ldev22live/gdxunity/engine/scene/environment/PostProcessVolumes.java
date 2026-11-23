package io.github.ldev22live.gdxunity.engine.scene.environment;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Finds the post-processing for a camera position (roadmap M6.2, TD-091): every enabled {@link PostProcessVolumeComponent} on an
 * active entity, weighted by where the camera is, blended in priority order (lowest first; equal priorities in scene order) into a
 * {@link PostProcessSettings}. Pure scene traversal, usable without a graphics context. Keeps its scratch space, so a renderer
 * calls {@link #resolve} every frame without allocating once its lists have grown; not thread-safe.
 */
public final class PostProcessVolumes {
    /** The volumes reaching the camera this frame and how strongly, in priority order once sorted. */
    private final List<PostProcessVolumeComponent> reaching = new ArrayList<>();
    private float[] amounts = new float[8];
    private final Matrix4 inverse = new Matrix4();
    private final Vector3 local = new Vector3();
    private final Vector3 nearest = new Vector3();

    /**
     * Writes into {@code out} the post-processing a camera at {@code camera} sees in {@code scene} and returns it; neutral when the
     * scene is null or inactive or no volume reaches the camera.
     */
    public PostProcessSettings resolve(Scene scene, Vector3 camera, PostProcessSettings out) {
        Objects.requireNonNull(camera, "camera cannot be null");
        Objects.requireNonNull(out, "out cannot be null");
        out.reset();
        reaching.clear();
        if (scene == null || !scene.isActive()) {
            return out;
        }
        for (Entity entity : scene.getAllEntities()) {
            if (!entity.isActiveInHierarchy()) continue;
            for (Component component : entity.getAllComponents()) {
                if (component.isEnabled() && component instanceof PostProcessVolumeComponent volume && volume.hasEffects()) {
                    float amount = amount(volume, entity.getTransform().getWorldMatrix(), camera);
                    if (amount > 0f) {
                        add(volume, amount);
                    }
                }
            }
        }
        for (int i = 0; i < reaching.size(); i++) {
            out.blend(reaching.get(i), amounts[i]);
        }
        reaching.clear();
        return out;
    }

    /** Inserts after every volume of the same or a lower priority, so equal priorities keep scene order. */
    private void add(PostProcessVolumeComponent volume, float amount) {
        int at = reaching.size();
        while (at > 0 && reaching.get(at - 1).getPriority() > volume.getPriority()) {
            at--;
        }
        if (reaching.size() == amounts.length) {
            amounts = Arrays.copyOf(amounts, amounts.length * 2);
        }
        System.arraycopy(amounts, at, amounts, at + 1, reaching.size() - at);
        amounts[at] = amount;
        reaching.add(at, volume);
    }

    /**
     * How strongly {@code volume}, placed by {@code world}, applies to a camera at {@code camera}: its weight inside the box (or
     * anywhere when global), fading linearly to 0 over its blend distance outside, and 0 farther away or when the entity is scaled
     * to nothing.
     */
    public float amount(PostProcessVolumeComponent volume, Matrix4 world, Vector3 camera) {
        Objects.requireNonNull(volume, "volume cannot be null");
        Objects.requireNonNull(world, "world cannot be null");
        Objects.requireNonNull(camera, "camera cannot be null");
        if (volume.isGlobal()) {
            return volume.getWeight();
        }
        if (Math.abs(world.det()) < Float.MIN_NORMAL) {
            return 0f;
        }
        inverse.set(world).inv();
        local.set(camera).mul(inverse);
        float halfX = volume.getSizeX() / 2f;
        float halfY = volume.getSizeY() / 2f;
        float halfZ = volume.getSizeZ() / 2f;
        nearest.set(clamp(local.x, -halfX, halfX), clamp(local.y, -halfY, halfY), clamp(local.z, -halfZ, halfZ));
        if (nearest.epsilonEquals(local, 0f)) {
            return volume.getWeight();
        }
        float blend = volume.getBlendDistance();
        if (blend <= 0f) {
            return 0f;
        }
        // The distance outside is measured in the world, so the entity's scale does not stretch the blend distance
        float outside = nearest.mul(world).dst(camera);
        return outside >= blend ? 0f : volume.getWeight() * (1f - outside / blend);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
