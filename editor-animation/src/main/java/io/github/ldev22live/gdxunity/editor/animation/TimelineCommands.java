package io.github.ldev22live.gdxunity.editor.animation;

import io.github.ldev22live.gdxunity.editor.core.command.EditorCommand;
import io.github.ldev22live.gdxunity.engine.animation.AnimationClip;
import io.github.ldev22live.gdxunity.engine.animation.AnimationTrack;
import io.github.ldev22live.gdxunity.engine.animation.Keyframe;

import java.util.List;
import java.util.Objects;

/** Undoable clip edits for a {@link TimelineDocument}'s history. */
public final class TimelineCommands {

    private TimelineCommands() {}

    /** Replaces all keys of one track; every key edit (add, move, delete, retime, change value) is one of these. */
    public static final class SetKeys implements EditorCommand {
        private final AnimationTrack track;
        private final List<Keyframe> before;
        private final List<Keyframe> after;
        private final String description;

        public SetKeys(AnimationTrack track, List<Keyframe> after, String description) {
            this.track = Objects.requireNonNull(track, "track cannot be null");
            this.before = List.copyOf(track.getCurve().getKeys());
            this.after = List.copyOf(after);
            this.description = Objects.requireNonNull(description, "description cannot be null");
        }

        @Override
        public boolean execute() {
            if (before.equals(after)) {
                return false;
            }
            track.getCurve().replaceKeys(after);
            return true;
        }

        @Override
        public boolean undo() {
            track.getCurve().replaceKeys(before);
            return true;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    public static final class AddTrack implements EditorCommand {
        private final AnimationClip clip;
        private final AnimationTrack track;
        private final int index;

        public AddTrack(AnimationClip clip, AnimationTrack track, int index) {
            this.clip = Objects.requireNonNull(clip, "clip cannot be null");
            this.track = Objects.requireNonNull(track, "track cannot be null");
            this.index = index;
        }

        @Override
        public boolean execute() {
            if (clip.findTrack(track.getTargetPath(), track.getPropertyId()) != null) {
                return false;
            }
            clip.addTrack(Math.min(index, clip.getTracks().size()), track);
            return true;
        }

        @Override
        public boolean undo() {
            return clip.removeTrack(track) >= 0;
        }

        @Override
        public String getDescription() {
            return "Add Track";
        }
    }

    public static final class RemoveTrack implements EditorCommand {
        private final AnimationClip clip;
        private final AnimationTrack track;
        private int index = -1;

        public RemoveTrack(AnimationClip clip, AnimationTrack track) {
            this.clip = Objects.requireNonNull(clip, "clip cannot be null");
            this.track = Objects.requireNonNull(track, "track cannot be null");
        }

        @Override
        public boolean execute() {
            index = clip.removeTrack(track);
            return index >= 0;
        }

        @Override
        public boolean undo() {
            clip.addTrack(index, track);
            return true;
        }

        @Override
        public String getDescription() {
            return "Remove Track";
        }
    }

    /** Changes the clip's length and frame rate together. */
    public static final class SetTiming implements EditorCommand {
        private final AnimationClip clip;
        private final float beforeDuration;
        private final int beforeFrameRate;
        private final float afterDuration;
        private final int afterFrameRate;

        public SetTiming(AnimationClip clip, float duration, int frameRate) {
            this.clip = Objects.requireNonNull(clip, "clip cannot be null");
            this.beforeDuration = clip.getDuration();
            this.beforeFrameRate = clip.getFrameRate();
            this.afterDuration = duration;
            this.afterFrameRate = frameRate;
        }

        @Override
        public boolean execute() {
            if (beforeDuration == afterDuration && beforeFrameRate == afterFrameRate) {
                return false;
            }
            clip.setDuration(afterDuration);
            clip.setFrameRate(afterFrameRate);
            return true;
        }

        @Override
        public boolean undo() {
            clip.setDuration(beforeDuration);
            clip.setFrameRate(beforeFrameRate);
            return true;
        }

        @Override
        public String getDescription() {
            return "Change Clip Length";
        }
    }
}
