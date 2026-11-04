package io.github.ldev22live.gdxunity.engine.animation.skeleton;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * Local translation, rotation and scale of every bone of a {@link Skeleton}, and forward kinematics: the
 * model-space transform of each bone is its parent's model transform times its own local transform.
 */
public final class Pose {

    private final Skeleton skeleton;
    private final Vector3[] translations;
    private final Quaternion[] rotations;
    private final Vector3[] scales;

    /** A pose with every bone at its rest transform. */
    public Pose(Skeleton skeleton) {
        this.skeleton = Objects.requireNonNull(skeleton, "skeleton cannot be null");
        int n = skeleton.size();
        translations = new Vector3[n];
        rotations = new Quaternion[n];
        scales = new Vector3[n];
        for (int i = 0; i < n; i++) {
            Skeleton.Bone bone = skeleton.getBone(i);
            translations[i] = new Vector3(bone.restTranslation());
            rotations[i] = new Quaternion(bone.restRotation());
            scales[i] = new Vector3(bone.restScale());
        }
    }

    public Skeleton getSkeleton() {
        return skeleton;
    }

    /** The bone's local translation; modify it in place to pose the bone. */
    public Vector3 translation(int bone) {
        return translations[bone];
    }

    /** The bone's local rotation; modify it in place to pose the bone. */
    public Quaternion rotation(int bone) {
        return rotations[bone];
    }

    /** The bone's local scale; modify it in place to pose the bone. */
    public Vector3 scale(int bone) {
        return scales[bone];
    }

    /**
     * Forward kinematics: fills {@code out[i]} with bone i's model-space transform.
     *
     * @param out at least {@link Skeleton#size()} matrices; reused to avoid allocation per frame
     */
    public void computeModelTransforms(Matrix4[] out) {
        if (out.length < skeleton.size()) {
            throw new IllegalArgumentException("Need " + skeleton.size() + " matrices, got " + out.length);
        }
        for (int i = 0; i < skeleton.size(); i++) {
            out[i].set(translations[i], rotations[i], scales[i]);
            int parent = skeleton.getBone(i).parent();
            if (parent >= 0) {
                out[i].mulLeft(out[parent]);
            }
        }
    }

    /** Model-space position of {@code bone}. */
    public Vector3 modelPosition(int bone) {
        return modelTransform(bone).getTranslation(new Vector3());
    }

    /** Model-space rotation of {@code bone}: the product of the local rotations from the root down (scale ignored). */
    public Quaternion modelRotation(int bone) {
        Quaternion result = new Quaternion(rotations[bone]);
        for (int i = skeleton.getBone(bone).parent(); i >= 0; i = skeleton.getBone(i).parent()) {
            result.mulLeft(rotations[i]);
        }
        return result;
    }

    private Matrix4 modelTransform(int bone) {
        Matrix4 result = new Matrix4().set(translations[bone], rotations[bone], scales[bone]);
        for (int i = skeleton.getBone(bone).parent(); i >= 0; i = skeleton.getBone(i).parent()) {
            result.mulLeft(new Matrix4().set(translations[i], rotations[i], scales[i]));
        }
        return result;
    }
}
