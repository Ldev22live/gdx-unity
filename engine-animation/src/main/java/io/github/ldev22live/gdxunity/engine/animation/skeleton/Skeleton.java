package io.github.ldev22live.gdxunity.engine.animation.skeleton;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A bone hierarchy with rest poses. Bones are stored parents first, so walking the list in order visits every
 * parent before its children (forward kinematics relies on this).
 */
public final class Skeleton {

    /**
     * One bone: its rest transform is relative to its parent bone (or to the model for a root bone).
     *
     * @param parent index of the parent bone, or -1 for a root bone
     */
    public record Bone(String name, int parent, Vector3 restTranslation, Quaternion restRotation, Vector3 restScale) {
        public Bone {
            Objects.requireNonNull(name, "name cannot be null");
            restTranslation = new Vector3(Objects.requireNonNull(restTranslation, "restTranslation cannot be null"));
            restRotation = new Quaternion(Objects.requireNonNull(restRotation, "restRotation cannot be null"));
            restScale = new Vector3(Objects.requireNonNull(restScale, "restScale cannot be null"));
        }
    }

    private final List<Bone> bones;

    /**
     * @throws IllegalArgumentException if a parent index does not point to an earlier bone
     */
    public Skeleton(List<Bone> bones) {
        List<Bone> copy = new ArrayList<>(Objects.requireNonNull(bones, "bones cannot be null"));
        for (int i = 0; i < copy.size(); i++) {
            int parent = copy.get(i).parent();
            if (parent < -1 || parent >= i) {
                throw new IllegalArgumentException("Bone " + i + " (" + copy.get(i).name()
                    + ") has parent " + parent + "; parents must come before their children");
            }
        }
        this.bones = Collections.unmodifiableList(copy);
    }

    public List<Bone> getBones() {
        return bones;
    }

    public int size() {
        return bones.size();
    }

    public Bone getBone(int index) {
        return bones.get(index);
    }

    /** Index of the first bone named {@code name}, or -1. */
    public int indexOf(String name) {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** True if {@code ancestor} is {@code bone} or one of its parents. */
    public boolean isAncestor(int ancestor, int bone) {
        for (int i = bone; i >= 0; i = bones.get(i).parent()) {
            if (i == ancestor) {
                return true;
            }
        }
        return false;
    }
}
