package io.github.ldev22live.gdxunity.engine.animation.skeleton;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Builds {@link Skeleton}s from imported models. */
public final class Skeletons {

    private Skeletons() {}

    /**
     * The skeleton of {@code model}: every node a skinned mesh uses as a bone, with the nearest bone above it as its
     * parent and its node transform as rest pose. An empty skeleton for models without skinning.
     */
    public static Skeleton fromModel(Model model) {
        Set<Node> boneNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node node : model.nodes) {
            collectBoneNodes(node, boneNodes);
        }
        List<Skeleton.Bone> bones = new ArrayList<>();
        for (Node node : model.nodes) {
            addBones(node, -1, boneNodes, bones);
        }
        return new Skeleton(bones);
    }

    private static void collectBoneNodes(Node node, Set<Node> boneNodes) {
        for (NodePart part : node.parts) {
            if (part.invBoneBindTransforms != null) {
                for (ObjectMap.Entry<Node, ?> entry : part.invBoneBindTransforms) {
                    boneNodes.add(entry.key);
                }
            }
        }
        for (Node child : node.getChildren()) {
            collectBoneNodes(child, boneNodes);
        }
    }

    private static void addBones(Node node, int parentBone, Set<Node> boneNodes, List<Skeleton.Bone> bones) {
        int index = parentBone;
        if (boneNodes.contains(node)) {
            index = bones.size();
            bones.add(new Skeleton.Bone(node.id == null ? "bone" + index : node.id, parentBone,
                node.translation, node.rotation, node.scale));
        }
        for (Node child : node.getChildren()) {
            addBones(child, index, boneNodes, bones);
        }
    }
}
