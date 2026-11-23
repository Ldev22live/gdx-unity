package io.github.ldev22live.gdxunity.engine.character;

import com.badlogic.gdx.math.Quaternion;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationCurve;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies the humanoid animation templates ({@code animations/humanoid/*.anim}, TD-044) to a character's skeleton (TD-053).
 *
 * <p>The templates animate a simple humanoid made of named parts (Hips, Spine, Chest, Neck, Head, Arm, Forearm, Thigh, Shin, left
 * and right) with local rotations as yaw, pitch and roll and the hips' height. The character's rig is built with every bone
 * unrotated at rest, so those rotations mean the same axes on its bones. Names are matched with {@link #BONE_OF}; the hips' height
 * moves the pelvis up and down from where it stands. Fingers, toes, clavicles and the other spine bones stay still.
 */
public final class HumanoidTemplates {
    /** The template part names and the rig bone each one moves. */
    public static final Map<String, String> BONE_OF = boneMap();
    /** Height of the template humanoid's hips in scene units: the hips' height tracks move the pelvis relative to this. */
    public static final float TEMPLATE_HIPS_HEIGHT = 100f;
    /** Meters per scene unit. */
    private static final float METERS_PER_UNIT = 0.01f;

    private static final String YAW = "transform.rotation.yaw";
    private static final String PITCH = "transform.rotation.pitch";
    private static final String ROLL = "transform.rotation.roll";
    private static final String POSITION_Y = "transform.position.y";

    private HumanoidTemplates() {}

    private static Map<String, String> boneMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Hips", "pelvis");
        map.put("Spine", "spine_01");
        map.put("Chest", "spine_03");
        map.put("Neck", "neck_01");
        map.put("Head", "head");
        for (String side : new String[] {"L", "R"}) {
            String suffix = side.toLowerCase();
            map.put("Arm " + side, "upperarm_" + suffix);
            map.put("Forearm " + side, "lowerarm_" + suffix);
            map.put("Thigh " + side, "thigh_" + suffix);
            map.put("Shin " + side, "calf_" + suffix);
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    /**
     * Samples {@code clip} onto {@code character}'s bones at the clip's frame rate.
     *
     * @return the animation; tracks that cannot be applied (a part the rig has no bone for, a property other than rotation and the
     *         hips' height) are listed in {@link BakedAnimation#problems()} and skipped
     */
    public static BakedAnimation bake(AnimationClip clip, BuiltCharacter character) {
        Objects.requireNonNull(clip, "clip cannot be null");
        Objects.requireNonNull(character, "character cannot be null");
        List<String> problems = new ArrayList<>();
        Map<String, Map<String, AnimationCurve>> byBone = new LinkedHashMap<>();
        for (AnimationTrack track : clip.getTracks()) {
            String path = track.getTargetPath();
            String part = path.substring(path.lastIndexOf('/') + 1);
            String bone = BONE_OF.get(part);
            boolean supported = YAW.equals(track.getPropertyId()) || PITCH.equals(track.getPropertyId())
                || ROLL.equals(track.getPropertyId()) || (POSITION_Y.equals(track.getPropertyId()) && part.equals("Hips"));
            if (bone == null) {
                problems.add(path + " " + track.getPropertyId() + ": the rig has no bone for " + part);
            } else if (!supported) {
                problems.add(path + " " + track.getPropertyId() + ": only rotation (and the hips' height) can be applied to a character");
            } else {
                byBone.computeIfAbsent(bone, b -> new LinkedHashMap<>()).put(track.getPropertyId(), track.getCurve());
            }
        }

        int frames = Math.max(2, Math.round(clip.getDuration() * clip.getFrameRate()) + 1);
        float[] times = new float[frames];
        for (int i = 0; i < frames; i++) {
            times[i] = clip.getDuration() * i / (frames - 1);
        }
        List<BakedAnimation.Channel> channels = new ArrayList<>();
        Quaternion q = new Quaternion();
        for (Map.Entry<String, Map<String, AnimationCurve>> entry : byBone.entrySet()) {
            int bone = indexOf(character, entry.getKey());
            Map<String, AnimationCurve> curves = entry.getValue();
            float[] rotations = null;
            if (curves.containsKey(YAW) || curves.containsKey(PITCH) || curves.containsKey(ROLL)) {
                rotations = new float[frames * 4];
                float lastX = 0f;
                float lastY = 0f;
                float lastZ = 0f;
                float lastW = 1f;
                for (int i = 0; i < frames; i++) {
                    q.setEulerAngles(value(curves.get(YAW), times[i]), value(curves.get(PITCH), times[i]), value(curves.get(ROLL), times[i]));
                    // q and -q are the same turn; keep neighbours on one side so playback does not spin the long way round
                    if (i > 0 && q.x * lastX + q.y * lastY + q.z * lastZ + q.w * lastW < 0f) {
                        q.set(-q.x, -q.y, -q.z, -q.w);
                    }
                    rotations[i * 4] = lastX = q.x;
                    rotations[i * 4 + 1] = lastY = q.y;
                    rotations[i * 4 + 2] = lastZ = q.z;
                    rotations[i * 4 + 3] = lastW = q.w;
                }
            }
            float[] translations = null;
            if (curves.containsKey(POSITION_Y)) {
                translations = new float[frames * 3];
                float[] rest = restTranslation(character, bone);
                for (int i = 0; i < frames; i++) {
                    translations[i * 3] = rest[0];
                    translations[i * 3 + 1] = rest[1] + (curves.get(POSITION_Y).evaluate(times[i]) - TEMPLATE_HIPS_HEIGHT) * METERS_PER_UNIT;
                    translations[i * 3 + 2] = rest[2];
                }
            }
            channels.add(new BakedAnimation.Channel(bone, translations, rotations));
        }
        return new BakedAnimation(clip.getName(), times, channels, problems);
    }

    private static float value(AnimationCurve curve, float time) {
        return curve == null ? 0f : curve.evaluate(time);
    }

    private static int indexOf(BuiltCharacter character, String bone) {
        for (int i = 0; i < character.bones().size(); i++) {
            if (character.bones().get(i).name().equals(bone)) {
                return i;
            }
        }
        throw new IllegalStateException("The rig has no bone " + bone);
    }

    /** A bone's translation in its parent's space at rest, meters. */
    static float[] restTranslation(BuiltCharacter character, int bone) {
        float[] head = character.jointHead(bone);
        int parent = character.bones().get(bone).parent();
        if (parent < 0) {
            return head;
        }
        float[] parentHead = character.jointHead(parent);
        return new float[] {head[0] - parentHead[0], head[1] - parentHead[1], head[2] - parentHead[2]};
    }
}
