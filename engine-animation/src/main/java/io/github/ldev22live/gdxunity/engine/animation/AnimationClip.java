package io.github.ldev22live.gdxunity.engine.animation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A named set of tracks with a length, stored as a {@code .anim} asset (TD-031) and played by an
 * {@link AnimatorComponent} on the entity it was made for.
 */
public class AnimationClip {

    public static final float DEFAULT_DURATION = 2f;
    /** Frames per second the Timeline snaps keys to. */
    public static final int DEFAULT_FRAME_RATE = 30;
    /** Shortest allowed clip, in seconds. */
    public static final float MIN_DURATION = 1f / 60f;
    public static final int MAX_FRAME_RATE = 240;

    private String name;
    private float duration;
    private int frameRate;
    private final List<AnimationTrack> tracks = new ArrayList<>();

    public AnimationClip(String name) {
        this(name, DEFAULT_DURATION, DEFAULT_FRAME_RATE);
    }

    public AnimationClip(String name, float duration, int frameRate) {
        setName(name);
        setDuration(duration);
        setFrameRate(frameRate);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
    }

    /** Length in seconds; keys after it are kept but not reached. */
    public float getDuration() {
        return duration;
    }

    public void setDuration(float duration) {
        if (!Float.isFinite(duration) || duration < MIN_DURATION) {
            throw new IllegalArgumentException("Clip duration must be at least " + MIN_DURATION + " s, got: " + duration);
        }
        this.duration = duration;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(int frameRate) {
        if (frameRate < 1 || frameRate > MAX_FRAME_RATE) {
            throw new IllegalArgumentException("Frame rate must be 1.." + MAX_FRAME_RATE + ", got: " + frameRate);
        }
        this.frameRate = frameRate;
    }

    /** {@code time} rounded to the nearest frame. */
    public float snapToFrame(float time) {
        return Math.round(time * frameRate) / (float) frameRate;
    }

    public List<AnimationTrack> getTracks() {
        return Collections.unmodifiableList(tracks);
    }

    /** The track for {@code property} on {@code targetPath}, or null. */
    public AnimationTrack findTrack(String targetPath, String propertyId) {
        for (AnimationTrack track : tracks) {
            if (track.targets(targetPath, propertyId)) {
                return track;
            }
        }
        return null;
    }

    /**
     * Inserts {@code track} at {@code index}.
     *
     * @throws IllegalArgumentException if the clip already has a track for the same entity and property
     */
    public void addTrack(int index, AnimationTrack track) {
        Objects.requireNonNull(track, "track cannot be null");
        if (findTrack(track.getTargetPath(), track.getPropertyId()) != null) {
            throw new IllegalArgumentException("Clip already animates " + track.getPropertyId()
                + " on '" + track.getTargetPath() + "'");
        }
        tracks.add(index, track);
    }

    public void addTrack(AnimationTrack track) {
        addTrack(tracks.size(), track);
    }

    /** Removes {@code track}; returns its former index, or -1. */
    public int removeTrack(AnimationTrack track) {
        int index = tracks.indexOf(track);
        if (index >= 0) {
            tracks.remove(index);
        }
        return index;
    }
}
