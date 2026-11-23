package io.github.ldev22live.gdxunity.engine.render;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Which objects (shapes and models) to draw (roadmap M3.6, TD-097): each gets a bounding sphere from its model's rest pose, and only
 * those that touch the camera's view are drawn in the lit pass, or the shadow map's area in the shadow pass. A model that is animated or
 * skinned may move outside its rest pose, so it is never culled. The spheres of models are kept while a model lives; nothing is
 * allocated per frame once every model has been seen.
 */
public final class ObjectCulling {
    /** Floats per sphere: centre x, y, z and radius. */
    public static final int STRIDE = 4;
    /** Marks a model that must always be drawn, in place of a sphere. */
    private static final float[] ALWAYS = new float[0];

    private final Map<Model, float[]> localSpheres = new WeakHashMap<>();
    private final BoundingBox box = new BoundingBox();
    private final Vector3 centre = new Vector3();
    private final Vector3 scale = new Vector3();

    /**
     * Writes the world bounding sphere of {@code instance} at {@code out[at .. at + 3]}.
     *
     * @return false when the instance must always be drawn (animated or skinned), and nothing was written
     */
    public boolean worldSphere(ModelInstance instance, float[] out, int at) {
        Objects.requireNonNull(instance, "instance cannot be null");
        float[] local = localSpheres.get(instance.model);
        if (local == null) {
            local = localSphere(instance);
            localSpheres.put(instance.model, local);
        }
        if (local == ALWAYS) {
            return false;
        }
        worldSphere(local, instance.transform, centre, scale, out, at);
        return true;
    }

    /** The sphere {@code local} (x, y, z, radius) moved by {@code transform}: its radius grows by the transform's largest scale. */
    static void worldSphere(float[] local, Matrix4 transform, Vector3 centre, Vector3 scale, float[] out, int at) {
        centre.set(local[0], local[1], local[2]).mul(transform);
        transform.getScale(scale);
        float largest = Math.max(Math.abs(scale.x), Math.max(Math.abs(scale.y), Math.abs(scale.z)));
        out[at] = centre.x;
        out[at + 1] = centre.y;
        out[at + 2] = centre.z;
        out[at + 3] = local[3] * largest;
    }

    /** Whether the sphere at {@code spheres[at]} touches {@code frustum}. */
    public static boolean touches(Frustum frustum, float[] spheres, int at) {
        return frustum.sphereInFrustum(spheres[at], spheres[at + 1], spheres[at + 2], spheres[at + 3]);
    }

    private float[] localSphere(ModelInstance instance) {
        if (instance.model.animations.size > 0 || skinned(instance.nodes)) {
            return ALWAYS;
        }
        // The rest pose in the model's own space: the instance's transform is applied per frame
        new ModelInstance(instance.model).calculateBoundingBox(box);
        if (!box.isValid()) {
            return ALWAYS;
        }
        box.getCenter(centre);
        return new float[] {centre.x, centre.y, centre.z, box.getDimensions(scale).len() / 2f};
    }

    private static boolean skinned(Iterable<Node> nodes) {
        for (Node node : nodes) {
            for (NodePart part : node.parts) {
                if (part.bones != null) {
                    return true;
                }
            }
            if (skinned(node.getChildren())) {
                return true;
            }
        }
        return false;
    }
}
