package io.github.ldev22live.gdxunity.engine.animation;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies a clip at a time to an entity tree, reports tracks that cannot play, and takes snapshots of what a clip
 * changes so an editor preview can put everything back.
 */
public final class ClipSampler {

    private ClipSampler() {}

    /**
     * Sets every playable track's property to its curve value at {@code time}. Tracks without keys, without a target
     * entity or with an unknown or missing property are skipped (see {@link #problems}).
     *
     * @return the number of tracks applied
     */
    public static int apply(AnimationClip clip, float time, Entity root, AnimatedProperties properties) {
        Objects.requireNonNull(clip, "clip cannot be null");
        Objects.requireNonNull(root, "root cannot be null");
        Objects.requireNonNull(properties, "properties cannot be null");
        int applied = 0;
        // Rotation channels of one entity are combined and written once
        Map<Entity, Vector3> rotations = new IdentityHashMap<>();
        for (AnimationTrack track : clip.getTracks()) {
            if (track.getCurve().isEmpty()) {
                continue;
            }
            Entity target = track.resolveTarget(root);
            AnimatedProperty property = properties.get(track.getPropertyId());
            if (target == null || property == null || !property.isAvailableOn(target)) {
                continue;
            }
            float value = track.getCurve().evaluate(time);
            if (AnimatedProperties.ROTATION_IDS.contains(property.id())) {
                Vector3 yawPitchRoll = rotations.computeIfAbsent(target, e -> e.getTransform().getLocalEulerAngles());
                switch (property.id()) {
                    case AnimatedProperties.ROTATION_YAW -> yawPitchRoll.x = value;
                    case AnimatedProperties.ROTATION_PITCH -> yawPitchRoll.y = value;
                    default -> yawPitchRoll.z = value;
                }
            } else {
                property.set(target, value);
            }
            applied++;
        }
        for (Map.Entry<Entity, Vector3> rotation : rotations.entrySet()) {
            Vector3 r = rotation.getValue();
            rotation.getKey().getTransform().setLocalEulerAngles(r.x, r.y, r.z);
        }
        return applied;
    }

    /** One line per track that cannot play on {@code root}, naming the track and the reason. Empty when all can. */
    public static List<String> problems(AnimationClip clip, Entity root, AnimatedProperties properties) {
        List<String> problems = new ArrayList<>();
        for (AnimationTrack track : clip.getTracks()) {
            String label = describe(track, properties);
            AnimatedProperty property = properties.get(track.getPropertyId());
            Entity target = track.resolveTarget(root);
            if (property == null) {
                problems.add(label + ": unknown property");
            } else if (target == null) {
                problems.add(label + ": no child '" + track.getTargetPath() + "' under " + root.getName());
            } else if (!property.isAvailableOn(target)) {
                problems.add(label + ": " + target.getName() + " has no " + property.group() + " component");
            }
        }
        return problems;
    }

    /** "Arm › Rotation Yaw" style label; the playing entity itself is "Self". */
    public static String describe(AnimationTrack track, AnimatedProperties properties) {
        AnimatedProperty property = properties.get(track.getPropertyId());
        String name = property != null ? property.displayName() : track.getPropertyId();
        String target = track.getTargetPath().isEmpty() ? "Self" : track.getTargetPath();
        return target + " › " + name;
    }

    /**
     * Records the current value of everything {@code clip} animates on {@code root}; {@link Snapshot#restore()}
     * writes them back. Rotations are recorded as quaternions so restoring is exact.
     */
    public static Snapshot capture(AnimationClip clip, Entity root, AnimatedProperties properties) {
        Map<Entity, Quaternion> rotations = new IdentityHashMap<>();
        Map<Entity, Map<AnimatedProperty, Float>> values = new LinkedHashMap<>();
        for (AnimationTrack track : clip.getTracks()) {
            Entity target = track.resolveTarget(root);
            AnimatedProperty property = properties.get(track.getPropertyId());
            if (target == null || property == null || !property.isAvailableOn(target)) {
                continue;
            }
            if (AnimatedProperties.ROTATION_IDS.contains(property.id())) {
                rotations.computeIfAbsent(target, e -> e.getTransform().getLocalRotation());
            } else {
                values.computeIfAbsent(target, e -> new LinkedHashMap<>()).put(property, property.get(target));
            }
        }
        return new Snapshot(rotations, values);
    }

    /** Values recorded by {@link #capture}. */
    public static final class Snapshot {
        private final Map<Entity, Quaternion> rotations;
        private final Map<Entity, Map<AnimatedProperty, Float>> values;

        private Snapshot(Map<Entity, Quaternion> rotations, Map<Entity, Map<AnimatedProperty, Float>> values) {
            this.rotations = rotations;
            this.values = values;
        }

        /** Writes the recorded values back to entities that still have the properties. */
        public void restore() {
            for (Map.Entry<Entity, Map<AnimatedProperty, Float>> entry : values.entrySet()) {
                for (Map.Entry<AnimatedProperty, Float> value : entry.getValue().entrySet()) {
                    if (value.getKey().isAvailableOn(entry.getKey())) {
                        value.getKey().set(entry.getKey(), value.getValue());
                    }
                }
            }
            for (Map.Entry<Entity, Quaternion> rotation : rotations.entrySet()) {
                rotation.getKey().getTransform().setLocalRotation(rotation.getValue());
            }
        }

        /** True if {@code entity} has recorded values. */
        public boolean covers(Entity entity) {
            return rotations.containsKey(entity) || values.containsKey(entity);
        }
    }
}
