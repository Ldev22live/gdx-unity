package io.github.ldev22live.gdxunity.engine.animation.skeleton;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import java.util.Objects;

/**
 * Analytic two-bone inverse kinematics (an arm: shoulder, elbow, hand; or a leg: hip, knee, foot). Rotates the
 * upper and lower bone so the end bone reaches a target, bending towards a pole point. Assumes the chain's bones
 * are not scaled. A target out of reach straightens the chain towards it.
 */
public final class TwoBoneIk {

    /** Keeps the chain from fully straightening or folding, where the bend direction becomes undefined. */
    private static final float REACH_EPSILON = 1e-4f;

    private TwoBoneIk() {}

    /**
     * Solves in place: changes the local rotations of {@code upper} and {@code lower} in {@code pose}.
     *
     * @param upper  bone at the root of the chain (shoulder / hip)
     * @param lower  child of {@code upper} (elbow / knee)
     * @param end    child of {@code lower} whose position should reach the target (hand / foot)
     * @param target model-space point to reach
     * @param pole   model-space point the middle joint bends towards
     * @return true if the target was within reach
     * @throws IllegalArgumentException if the bones do not form a parent → child → child chain
     */
    public static boolean solve(Pose pose, int upper, int lower, int end, Vector3 target, Vector3 pole) {
        Objects.requireNonNull(pose, "pose cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(pole, "pole cannot be null");
        Skeleton skeleton = pose.getSkeleton();
        if (skeleton.getBone(lower).parent() != upper || skeleton.getBone(end).parent() != lower) {
            throw new IllegalArgumentException("Bones " + upper + ", " + lower + ", " + end + " are not a parent-child chain");
        }

        Vector3 a = pose.modelPosition(upper);
        Vector3 b = pose.modelPosition(lower);
        Vector3 c = pose.modelPosition(end);
        float lengthAb = b.dst(a);
        float lengthCb = c.dst(b);
        float distance = target.dst(a);
        boolean reachable = distance <= lengthAb + lengthCb;
        float lengthAt = MathUtils.clamp(distance, REACH_EPSILON, lengthAb + lengthCb - REACH_EPSILON);

        Vector3 ac = new Vector3(c).sub(a).nor();
        Vector3 ab = new Vector3(b).sub(a).nor();
        Vector3 ba = new Vector3(a).sub(b).nor();
        Vector3 bc = new Vector3(c).sub(b).nor();
        Vector3 at = new Vector3(target).sub(a).nor();

        // Current and wanted inner angles at the upper and middle joints (law of cosines)
        float angleCab0 = acos(ac.dot(ab));
        float angleAbc0 = acos(ba.dot(bc));
        float angleCat0 = acos(ac.dot(at));
        float angleCab1 = acos((lengthCb * lengthCb - lengthAb * lengthAb - lengthAt * lengthAt) / (-2f * lengthAb * lengthAt));
        float angleAbc1 = acos((lengthAt * lengthAt - lengthAb * lengthAb - lengthCb * lengthCb) / (-2f * lengthAb * lengthCb));

        // Bend axis from the pole; swing axis turns the straightened chain onto the target
        Vector3 bendAxis = new Vector3(c).sub(a).crs(new Vector3(pole).sub(a)).nor();
        Vector3 swingAxis = new Vector3(c).sub(a).crs(new Vector3(target).sub(a));
        if (swingAxis.isZero(REACH_EPSILON)) {
            swingAxis.set(bendAxis);
        }
        swingAxis.nor();
        if (bendAxis.isZero()) {
            // Pole on the chain line: any perpendicular works
            bendAxis.set(ac).crs(Vector3.Z);
            if (bendAxis.isZero(REACH_EPSILON)) {
                bendAxis.set(ac).crs(Vector3.X);
            }
            bendAxis.nor();
        }

        Quaternion upperModel = pose.modelRotation(upper);
        Quaternion lowerModel = pose.modelRotation(lower);
        Quaternion r0 = axisAngle(toLocal(upperModel, bendAxis), angleCab1 - angleCab0);
        Quaternion r1 = axisAngle(toLocal(lowerModel, bendAxis), angleAbc1 - angleAbc0);
        Quaternion r2 = axisAngle(toLocal(upperModel, swingAxis), angleCat0);

        pose.rotation(upper).mul(new Quaternion(r0).mul(r2));
        pose.rotation(lower).mul(r1);
        return reachable;
    }

    private static float acos(float cosine) {
        return (float) Math.acos(MathUtils.clamp(cosine, -1f, 1f));
    }

    /** Expresses a model-space axis in the local frame of a bone with model rotation {@code model}. */
    private static Vector3 toLocal(Quaternion model, Vector3 axis) {
        return new Quaternion(model).conjugate().transform(new Vector3(axis));
    }

    private static Quaternion axisAngle(Vector3 axis, float radians) {
        return new Quaternion().setFromAxisRad(axis, radians);
    }
}
