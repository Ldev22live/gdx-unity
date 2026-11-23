package io.github.ldev22live.gdxunity.editor.animation;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.command.CommandHistory;
import io.github.ldev22live.gdxunity.engine.animation.AnimatedProperties;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.ClipSampler;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetReference;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Timeline's state, without UI: the open clip, the entity it animates, the play head, editor playback (play,
 * loop, reverse) and auto-key.
 *
 * <p><b>Preview.</b> While the play head is shown, the scene displays the clip's pose at that time. Before the first
 * pose the session records the animated values ({@link ClipSampler#capture}); {@link #endPreview()} puts them back.
 * The preview ends before play starts, before the scene is saved, when the scene changes and when the clip closes,
 * so a preview pose is never saved or carried into play. Changes to animated values that were not keyed are undone
 * when the preview ends, as in other sequencers.
 *
 * <p><b>Auto-key.</b> With auto-key on, each new scene edit (not undo) while previewing keys every track whose value
 * now differs from its curve, as one Timeline undo step.
 */
public class TimelineSession {

    public interface Listener {
        /** The clip, play head, playback or preview state changed. */
        void onTimelineChanged(TimelineSession session);
    }

    private final EditorState editorState;
    private final AnimationAssets assets;
    private final AnimatedProperties properties;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private TimelineDocument document;
    private UUID rootId;
    private float time;
    private boolean playing;
    private boolean looping = true;
    private boolean reverse;
    private boolean autoKey;

    private ClipSampler.Snapshot snapshot;
    private Scene previewScene;
    /** Set while the session itself writes a pose, so the scene listeners do not react to it. */
    private boolean applyingPose;

    private final TimelineDocument.Listener documentListener = d -> {
        refreshPreview();
        notifyChanged();
    };

    public TimelineSession(EditorState editorState, AnimationAssets assets, AnimatedProperties properties) {
        this.editorState = Objects.requireNonNull(editorState, "editorState cannot be null");
        this.assets = Objects.requireNonNull(assets, "assets cannot be null");
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        editorState.addListener(new EditorState.Listener() {
            @Override
            public void onActiveSceneChanged(Scene oldScene, Scene newScene) {
                endPreview();
            }
        });
        editorState.getCommandHistory().addListener(this::onSceneHistoryChanged);
    }

    public AnimatedProperties getProperties() {
        return properties;
    }

    public AnimationAssets getAssets() {
        return assets;
    }

    // --- Open / close ---

    /**
     * Opens a clip to animate the entity {@code rootId} (the entity with the Animator). The current clip is saved
     * first if it has changes.
     *
     * @return false if the clip could not be read; the status bar says why
     */
    public boolean open(AssetReference asset, UUID rootId) {
        Objects.requireNonNull(asset, "asset cannot be null");
        if (!saveIfDirty()) {
            return false;
        }
        AnimationClip clip;
        try {
            clip = assets.load(asset);
        } catch (IOException | RuntimeException e) {
            editorState.setStatusMessage("Cannot open " + asset.getFileName() + ": " + e.getMessage(), true);
            return false;
        }
        close();
        document = new TimelineDocument(asset, clip, properties);
        document.addListener(documentListener);
        this.rootId = rootId;
        this.time = 0f;
        this.playing = false;
        notifyChanged();
        return true;
    }

    /** Ends the preview and closes the clip without saving. */
    public void close() {
        endPreview();
        playing = false;
        if (document != null) {
            document.removeListener(documentListener);
            document = null;
        }
        rootId = null;
        notifyChanged();
    }

    /** Saves the open clip if it has changes; returns false (and reports) if saving failed. */
    public boolean saveIfDirty() {
        if (document == null || !document.isDirty()) {
            return true;
        }
        try {
            document.save(assets);
            return true;
        } catch (IOException e) {
            editorState.setStatusMessage("Could not save " + document.getAsset().getFileName() + ": " + e.getMessage(), true);
            return false;
        }
    }

    public TimelineDocument getDocument() {
        return document;
    }

    public UUID getRootId() {
        return rootId;
    }

    /** The entity the clip animates, in the scene being edited, or null if it is gone (or nothing is open). */
    public Entity getRootEntity() {
        Scene scene = editorState.getActiveScene();
        return (rootId == null || scene == null) ? null : scene.findEntityById(rootId);
    }

    // --- Play head and playback ---

    public float getTime() {
        return time;
    }

    /** Moves the play head (snapped to frames) and shows the pose there. */
    public void setTime(float newTime) {
        if (document == null) {
            return;
        }
        time = document.snap(newTime);
        showPose();
        notifyChanged();
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing && document != null;
        notifyChanged();
    }

    public boolean isLooping() {
        return looping;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
        notifyChanged();
    }

    public boolean isReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
        notifyChanged();
    }

    public boolean isAutoKey() {
        return autoKey;
    }

    public void setAutoKey(boolean autoKey) {
        this.autoKey = autoKey;
        notifyChanged();
    }

    /** Advances editor playback; call every frame. Not snapped to frames, so playback is smooth. */
    public void update(float deltaTime) {
        if (!playing || document == null) {
            return;
        }
        if (editorState.isPlayModeActive()) {
            playing = false;
            notifyChanged();
            return;
        }
        float duration = document.getClip().getDuration();
        float next = time + (reverse ? -deltaTime : deltaTime);
        if (next > duration || next < 0f) {
            if (looping) {
                next = ((next % duration) + duration) % duration;
            } else {
                next = Math.max(0f, Math.min(duration, next));
                playing = false;
            }
        }
        time = next;
        showPose();
        notifyChanged();
    }

    // --- Keying ---

    /** Keys the selected entity's tracks at the play head (adding transform tracks if it has none). */
    public boolean keySelection() {
        Entity root = getRootEntity();
        Entity selected = editorState.getSelectedEntity();
        if (document == null || root == null) {
            editorState.setStatusMessage("Open a clip from an Animator first", true);
            return false;
        }
        Entity target = selected != null ? selected : root;
        int keyed = document.keyEntity(root, target, time);
        if (keyed < 0) {
            editorState.setStatusMessage(target.getName() + " is not " + root.getName() + " or one of its children", true);
            return false;
        }
        editorState.setStatusMessage("Keyed " + keyed + " value(s) of " + target.getName() + " at " + format(time) + " s", false);
        return keyed > 0;
    }

    // --- Preview ---

    public boolean isPreviewing() {
        return snapshot != null;
    }

    /** Puts back everything the preview changed and stops editor playback. */
    public void endPreview() {
        playing = false;
        if (snapshot == null) {
            return;
        }
        applyingPose = true;
        try {
            snapshot.restore();
        } finally {
            applyingPose = false;
        }
        snapshot = null;
        previewScene = null;
        notifyChanged();
    }

    private void showPose() {
        Entity root = getRootEntity();
        if (document == null || root == null || editorState.isPlayModeActive()) {
            return;
        }
        if (snapshot == null || previewScene != editorState.getActiveScene()) {
            snapshot = ClipSampler.capture(document.getClip(), root, properties);
            previewScene = editorState.getActiveScene();
        }
        applyingPose = true;
        try {
            ClipSampler.apply(document.getClip(), time, root, properties);
        } finally {
            applyingPose = false;
        }
    }

    /** After a clip edit: record values of newly animated properties, then show the edited pose. */
    private void refreshPreview() {
        if (snapshot == null) {
            return;
        }
        applyingPose = true;
        try {
            snapshot.restore();
        } finally {
            applyingPose = false;
        }
        snapshot = null;
        showPose();
    }

    private void onSceneHistoryChanged(CommandHistory history) {
        // Only new edits (an undo leaves something to redo) while previewing with auto-key
        if (applyingPose || !autoKey || snapshot == null || document == null || history.getRedoCount() > 0) {
            return;
        }
        Entity root = getRootEntity();
        if (root != null) {
            document.recordChanges(root, time);
        }
    }

    private static String format(float seconds) {
        return String.format(java.util.Locale.ROOT, "%.2f", seconds);
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener cannot be null"));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onTimelineChanged(this);
        }
    }
}
