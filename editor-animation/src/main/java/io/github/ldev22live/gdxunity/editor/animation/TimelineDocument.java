package io.github.ldev22live.gdxunity.editor.animation;

import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.editor.core.command.CompoundCommand;
import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperty;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationCurve;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.Interpolation;
import io.github.ldev22live.gdxunity.engine.animation.Keyframe;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A clip open in the Timeline: the asset it came from, its own undo history (separate from the scene's, like a
 * graph's), and whether it has unsaved changes. Every edit is one undo step; times are snapped to the clip's frames.
 */
public class TimelineDocument {

    /** A key identified by its track and time (keys are immutable, so this is how the UI selects them). */
    public record KeyRef(AnimationTrack track, float time) {
        public KeyRef {
            Objects.requireNonNull(track, "track cannot be null");
        }

        /** The key this refers to now, or null if it no longer exists. */
        public Keyframe resolve() {
            int index = track.getCurve().indexAt(time);
            return index >= 0 ? track.getCurve().getKey(index) : null;
        }
    }

    public interface Listener {
        void onDocumentChanged(TimelineDocument document);
    }

    /** Values closer than this count as unchanged when auto-keying. */
    public static final float VALUE_EPSILON = 1e-4f;
    /** Interpolation of keys created from the Timeline: smooth, eased at the ends. */
    public static final Interpolation DEFAULT_INTERPOLATION = Interpolation.BEZIER;

    private final AssetReference asset;
    private final AnimationClip clip;
    private final AnimatedProperties properties;
    private final CommandHistory history = new CommandHistory();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean dirty;

    public TimelineDocument(AssetReference asset, AnimationClip clip, AnimatedProperties properties) {
        this.asset = Objects.requireNonNull(asset, "asset cannot be null");
        this.clip = Objects.requireNonNull(clip, "clip cannot be null");
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        history.addListener(h -> {
            dirty = true;
            notifyChanged();
        });
    }

    public AssetReference getAsset() {
        return asset;
    }

    public AnimationClip getClip() {
        return clip;
    }

    public AnimatedProperties getProperties() {
        return properties;
    }

    public CommandHistory getHistory() {
        return history;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean execute(EditorCommand command) {
        return history.execute(command);
    }

    public boolean undo() {
        return history.undo();
    }

    public boolean redo() {
        return history.redo();
    }

    public void save(AnimationAssets assets) throws IOException {
        assets.save(asset, clip);
        dirty = false;
        notifyChanged();
    }

    /** Clamps to the clip and snaps to its frames. */
    public float snap(float time) {
        return clip.snapToFrame(Math.max(0f, Math.min(clip.getDuration(), time)));
    }

    // --- Tracks ---

    /**
     * Adds an empty track for {@code propertyId} on the entity at {@code targetPath}.
     *
     * @return the new track, or null if the clip already has it or the property is unknown
     */
    public AnimationTrack addTrack(String targetPath, String propertyId) {
        if (properties.get(propertyId) == null || clip.findTrack(targetPath, propertyId) != null) {
            return null;
        }
        AnimationTrack track = new AnimationTrack(targetPath, propertyId, new AnimationCurve());
        return execute(new TimelineCommands.AddTrack(clip, track, insertIndexFor(targetPath))) ? track : null;
    }

    public boolean removeTrack(AnimationTrack track) {
        return execute(new TimelineCommands.RemoveTrack(clip, track));
    }

    /** New tracks go after the last track of the same entity, so an entity's tracks stay together. */
    private int insertIndexFor(String targetPath) {
        List<AnimationTrack> tracks = clip.getTracks();
        for (int i = tracks.size() - 1; i >= 0; i--) {
            if (tracks.get(i).getTargetPath().equals(targetPath)) {
                return i + 1;
            }
        }
        return tracks.size();
    }

    // --- Keys ---

    /** Sets (adds or replaces) a key at the snapped {@code time}. */
    public boolean setKey(AnimationTrack track, float time, float value) {
        float at = snap(time);
        int existing = track.getCurve().indexAt(at);
        Interpolation interpolation = existing >= 0 ? track.getCurve().getKey(existing).interpolation() : DEFAULT_INTERPOLATION;
        List<Keyframe> keys = new ArrayList<>(track.getCurve().getKeys());
        keys.add(new Keyframe(at, value, interpolation));
        return execute(new TimelineCommands.SetKeys(track, keys, "Set Key"));
    }

    /**
     * Keys the current values of {@code target}'s tracks at {@code time}. An entity without tracks gets the nine
     * transform tracks first. One undo step.
     *
     * @param root the entity the clip plays on
     * @return the number of keys set, or -1 if {@code target} is not {@code root} or below it
     */
    public int keyEntity(Entity root, Entity target, float time) {
        String path = AnimationTrack.pathOf(root, target);
        if (path == null) {
            return -1;
        }
        CompoundCommand compound = new CompoundCommand("Key " + target.getName());
        List<AnimationTrack> tracks = tracksOf(path);
        if (tracks.isEmpty()) {
            int index = insertIndexFor(path);
            for (String id : AnimatedProperties.TRANSFORM_IDS) {
                AnimationTrack track = new AnimationTrack(path, id, new AnimationCurve());
                compound.addCommand(new TimelineCommands.AddTrack(clip, track, index++));
                tracks.add(track);
            }
        }
        float at = snap(time);
        int keyed = 0;
        for (AnimationTrack track : tracks) {
            AnimatedProperty property = properties.get(track.getPropertyId());
            if (property == null || !property.isAvailableOn(target)) {
                continue;
            }
            List<Keyframe> keys = new ArrayList<>(track.getCurve().getKeys());
            keys.add(new Keyframe(at, property.get(target), interpolationAt(track, at)));
            compound.addCommand(new TimelineCommands.SetKeys(track, keys, "Set Key"));
            keyed++;
        }
        return execute(compound) ? keyed : 0;
    }

    /**
     * Auto-key: for every track whose entity's current value differs from the curve at {@code time}, sets a key with
     * the current value. One undo step; nothing is recorded when nothing changed.
     *
     * @return the number of keys set
     */
    public int recordChanges(Entity root, float time) {
        float at = snap(time);
        CompoundCommand compound = new CompoundCommand("Auto Key");
        int keyed = 0;
        for (AnimationTrack track : clip.getTracks()) {
            Entity target = track.resolveTarget(root);
            AnimatedProperty property = properties.get(track.getPropertyId());
            if (target == null || property == null || !property.isAvailableOn(target)) {
                continue;
            }
            float current = property.get(target);
            AnimationCurve curve = track.getCurve();
            if (!curve.isEmpty() && Math.abs(curve.evaluate(at) - current) <= VALUE_EPSILON) {
                continue;
            }
            List<Keyframe> keys = new ArrayList<>(curve.getKeys());
            keys.add(new Keyframe(at, current, interpolationAt(track, at)));
            compound.addCommand(new TimelineCommands.SetKeys(track, keys, "Set Key"));
            keyed++;
        }
        return keyed > 0 && execute(compound) ? keyed : 0;
    }

    public boolean deleteKeys(Collection<KeyRef> keys) {
        Map<AnimationTrack, List<KeyRef>> byTrack = groupByTrack(keys);
        CompoundCommand compound = new CompoundCommand(keys.size() == 1 ? "Delete Key" : "Delete Keys");
        for (Map.Entry<AnimationTrack, List<KeyRef>> entry : byTrack.entrySet()) {
            List<Keyframe> remaining = new ArrayList<>(entry.getKey().getCurve().getKeys());
            for (KeyRef ref : entry.getValue()) {
                remaining.removeIf(k -> Math.abs(k.time() - ref.time()) <= AnimationCurve.TIME_EPSILON);
            }
            compound.addCommand(new TimelineCommands.SetKeys(entry.getKey(), remaining, "Delete Keys"));
        }
        return compound.getCommandCount() > 0 && execute(compound);
    }

    /**
     * Moves keys by {@code deltaTime} (snapped to frames, kept within the clip). A moved key landing on another key of
     * the same track replaces it.
     *
     * @return the keys at their new times (for keeping them selected), or an empty list if nothing moved
     */
    public List<KeyRef> moveKeys(Collection<KeyRef> keys, float deltaTime) {
        Map<AnimationTrack, List<KeyRef>> byTrack = groupByTrack(keys);
        CompoundCommand compound = new CompoundCommand(keys.size() == 1 ? "Move Key" : "Move Keys");
        List<KeyRef> moved = new ArrayList<>();
        for (Map.Entry<AnimationTrack, List<KeyRef>> entry : byTrack.entrySet()) {
            AnimationTrack track = entry.getKey();
            List<Keyframe> staying = new ArrayList<>(track.getCurve().getKeys());
            List<Keyframe> movedKeys = new ArrayList<>();
            for (KeyRef ref : entry.getValue()) {
                Keyframe key = ref.resolve();
                if (key == null) {
                    continue;
                }
                staying.remove(key);
                Keyframe shifted = key.withTime(snap(key.time() + deltaTime));
                movedKeys.add(shifted);
                moved.add(new KeyRef(track, shifted.time()));
            }
            List<Keyframe> result = new ArrayList<>(staying);
            result.addAll(movedKeys);
            compound.addCommand(new TimelineCommands.SetKeys(track, result, "Move Keys"));
        }
        return compound.getCommandCount() > 0 && execute(compound) ? moved : List.of();
    }

    public boolean setInterpolation(Collection<KeyRef> keys, Interpolation interpolation) {
        Objects.requireNonNull(interpolation, "interpolation cannot be null");
        CompoundCommand compound = new CompoundCommand("Change Interpolation");
        for (Map.Entry<AnimationTrack, List<KeyRef>> entry : groupByTrack(keys).entrySet()) {
            List<Keyframe> result = new ArrayList<>(entry.getKey().getCurve().getKeys());
            for (KeyRef ref : entry.getValue()) {
                Keyframe key = ref.resolve();
                if (key != null) {
                    result.set(result.indexOf(key), key.withInterpolation(interpolation));
                }
            }
            compound.addCommand(new TimelineCommands.SetKeys(entry.getKey(), result, "Change Interpolation"));
        }
        return compound.getCommandCount() > 0 && execute(compound);
    }

    /** Changes one key's value (curve view drag or typed value). */
    public boolean setKeyValue(KeyRef ref, float value) {
        Keyframe key = ref.resolve();
        if (key == null) {
            return false;
        }
        List<Keyframe> result = new ArrayList<>(ref.track().getCurve().getKeys());
        result.set(result.indexOf(key), key.withValue(value));
        return execute(new TimelineCommands.SetKeys(ref.track(), result, "Change Key Value"));
    }

    /**
     * Moves one key to a new (snapped) time and value in one step, as a curve-view drag does. A key already at the
     * new time on the same track is replaced.
     *
     * @return the key at its new place, or null if nothing changed
     */
    public KeyRef editKey(KeyRef ref, float time, float value) {
        Keyframe key = ref.resolve();
        if (key == null) {
            return null;
        }
        float at = snap(time);
        List<Keyframe> result = new ArrayList<>(ref.track().getCurve().getKeys());
        result.remove(key);
        result.removeIf(k -> Math.abs(k.time() - at) <= AnimationCurve.TIME_EPSILON);
        result.add(new Keyframe(at, value, key.interpolation()));
        return execute(new TimelineCommands.SetKeys(ref.track(), result, "Edit Key")) ? new KeyRef(ref.track(), at) : null;
    }

    /**
     * @return false (with nothing changed) if the length or frame rate is out of range
     */
    public boolean setTiming(float duration, int frameRate) {
        if (!Float.isFinite(duration) || duration < AnimationClip.MIN_DURATION
            || frameRate < 1 || frameRate > AnimationClip.MAX_FRAME_RATE) {
            return false;
        }
        return execute(new TimelineCommands.SetTiming(clip, duration, frameRate));
    }

    /** Tracks of the entity at {@code path}, in clip order. */
    public List<AnimationTrack> tracksOf(String path) {
        List<AnimationTrack> result = new ArrayList<>();
        for (AnimationTrack track : clip.getTracks()) {
            if (track.getTargetPath().equals(path)) {
                result.add(track);
            }
        }
        return result;
    }

    /** Keeps the interpolation of a key being replaced; new keys get {@link #DEFAULT_INTERPOLATION}. */
    private static Interpolation interpolationAt(AnimationTrack track, float time) {
        int index = track.getCurve().indexAt(time);
        return index >= 0 ? track.getCurve().getKey(index).interpolation() : DEFAULT_INTERPOLATION;
    }

    private static Map<AnimationTrack, List<KeyRef>> groupByTrack(Collection<KeyRef> keys) {
        Map<AnimationTrack, List<KeyRef>> byTrack = new LinkedHashMap<>();
        for (KeyRef ref : keys) {
            byTrack.computeIfAbsent(ref.track(), t -> new ArrayList<>()).add(ref);
        }
        return byTrack;
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onDocumentChanged(this);
        }
    }
}
