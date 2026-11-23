package io.github.ldev22live.gdxunity.engine.animation.skeleton;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ArrayMap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkeletonTest {

    private static final float EPSILON = 1e-3f;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    /** Shoulder at the origin, elbow 1 up, hand 1 further up: a straight arm of length 2 along +Y. */
    private static Skeleton arm() {
        Quaternion identity = new Quaternion();
        Vector3 one = new Vector3(1f, 1f, 1f);
        return new Skeleton(List.of(
            new Skeleton.Bone("Shoulder", -1, new Vector3(), identity, one),
            new Skeleton.Bone("Elbow", 0, new Vector3(0f, 1f, 0f), identity, one),
            new Skeleton.Bone("Hand", 1, new Vector3(0f, 1f, 0f), identity, one)));
    }

    private static void assertNear(Vector3 expected, Vector3 actual) {
        assertTrue(expected.epsilonEquals(actual, EPSILON), "expected " + expected + " but was " + actual);
    }

    @Test
    void shouldComputeModelTransformsDownTheChain() {
        Pose pose = new Pose(arm());
        pose.rotation(0).setFromAxis(Vector3.Z, 90f);
        Matrix4[] out = {new Matrix4(), new Matrix4(), new Matrix4()};

        pose.computeModelTransforms(out);

        // Rotating the shoulder 90 degrees about Z swings the arm from +Y to -X
        assertNear(new Vector3(-1f, 0f, 0f), out[1].getTranslation(new Vector3()));
        assertNear(new Vector3(-2f, 0f, 0f), out[2].getTranslation(new Vector3()));
        assertNear(new Vector3(-2f, 0f, 0f), pose.modelPosition(2));
    }

    @Test
    void shouldReachTargetWithTwoBoneIk() {
        Pose pose = new Pose(arm());
        Vector3 target = new Vector3(1f, 1f, 0f);

        boolean reachable = TwoBoneIk.solve(pose, 0, 1, 2, target, new Vector3(-1f, 1f, 0f));

        assertTrue(reachable);
        assertNear(target, pose.modelPosition(2));
        // Bone lengths are unchanged
        assertEquals(1f, pose.modelPosition(1).dst(pose.modelPosition(0)), EPSILON);
        assertEquals(1f, pose.modelPosition(2).dst(pose.modelPosition(1)), EPSILON);
    }

    @Test
    void shouldBendTowardsPole() {
        Pose towardsMinusX = new Pose(arm());
        Pose towardsPlusX = new Pose(arm());
        Vector3 target = new Vector3(0f, 1.5f, 0f);

        TwoBoneIk.solve(towardsMinusX, 0, 1, 2, target, new Vector3(-5f, 0.75f, 0f));
        TwoBoneIk.solve(towardsPlusX, 0, 1, 2, target, new Vector3(5f, 0.75f, 0f));

        assertNear(target, towardsMinusX.modelPosition(2));
        assertNear(target, towardsPlusX.modelPosition(2));
        assertTrue(towardsMinusX.modelPosition(1).x < -0.1f, "elbow should bend to -X");
        assertTrue(towardsPlusX.modelPosition(1).x > 0.1f, "elbow should bend to +X");
    }

    @Test
    void shouldStraightenTowardsTargetOutOfReach() {
        Pose pose = new Pose(arm());

        boolean reachable = TwoBoneIk.solve(pose, 0, 1, 2, new Vector3(10f, 0f, 0f), new Vector3(0f, 0f, 1f));

        assertFalse(reachable);
        // The solver keeps a hair of bend so the bend direction stays defined: the hand ends within 0.03 of straight
        assertTrue(pose.modelPosition(2).dst(2f, 0f, 0f) < 0.03f, "hand at " + pose.modelPosition(2));
    }

    @Test
    void shouldRejectBonesThatAreNotAChain() {
        assertThrows(IllegalArgumentException.class,
            () -> TwoBoneIk.solve(new Pose(arm()), 0, 2, 1, new Vector3(), new Vector3()));
        assertThrows(IllegalArgumentException.class, () -> new Skeleton(List.of(
            new Skeleton.Bone("A", 0, new Vector3(), new Quaternion(), new Vector3(1f, 1f, 1f)))));
    }

    @Test
    void shouldBuildSkeletonFromSkinnedModelNodes() {
        Model model = new Model();
        Node mesh = new Node();
        mesh.id = "Mesh";
        Node hips = new Node();
        hips.id = "Hips";
        Node group = new Node();
        group.id = "NotABone";
        Node knee = new Node();
        knee.id = "Knee";
        knee.translation.set(0f, -1f, 0f);
        hips.addChild(group);
        group.addChild(knee);
        NodePart part = new NodePart();
        part.invBoneBindTransforms = new ArrayMap<>();
        part.invBoneBindTransforms.put(hips, new Matrix4());
        part.invBoneBindTransforms.put(knee, new Matrix4());
        mesh.parts.add(part);
        model.nodes.add(mesh);
        model.nodes.add(hips);

        Skeleton skeleton = Skeletons.fromModel(model);

        assertEquals(2, skeleton.size());
        assertEquals("Hips", skeleton.getBone(0).name());
        assertEquals(-1, skeleton.getBone(0).parent());
        // The plain node between them is skipped; Knee's parent bone is Hips
        assertEquals(0, skeleton.getBone(skeleton.indexOf("Knee")).parent());
        assertEquals(new Vector3(0f, -1f, 0f), skeleton.getBone(1).restTranslation());
        assertTrue(skeleton.isAncestor(0, 1));
    }
}
