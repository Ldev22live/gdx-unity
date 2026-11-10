package io.github.ldev22live.gdxunity.engine.sprite;

import java.util.Objects;

/**
 * The playback state of one sprite animation: which tag plays, how long it has played, at what speed, looping or not (TD-066, milestone D).
 * Headless, so the component that draws it and the tests share the same logic.
 */
public final class SpriteAnimationState {
    private static final double MILLIS_PER_SECOND = 1000.0;

    private final SpriteDocument doc;
    private String tag;
    private double elapsedMillis;
    private float speed = 1f;
    private boolean loop = true;
    private boolean playing = true;

    public SpriteAnimationState(SpriteDocument doc) {
        this.doc = Objects.requireNonNull(doc, "doc cannot be null");
    }

    public SpriteDocument getDocument() {
        return doc;
    }

    private SpriteTag find(String name) {
        if (name == null) {
            return null;
        }
        for (SpriteTag t : doc.getTags()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        StringBuilder known = new StringBuilder();
        for (SpriteTag t : doc.getTags()) {
            known.append(known.length() == 0 ? "" : ", ").append(t.name());
        }
        throw new IllegalArgumentException("The sprite has no tag \"" + name + "\" (tags: " + (known.length() == 0 ? "none" : known) + ")");
    }

    /**
     * Starts playing a tag (null: all frames) and keeps playing from the start. Playing the tag that already plays continues it unless
     * {@code restart} is true, so a script can call {@code play("walk")} every frame.
     *
     * @throws IllegalArgumentException when the sprite has no tag with that name
     */
    public void play(String tagName, boolean restart) {
        find(tagName);
        boolean same = Objects.equals(tagName, tag);
        tag = tagName;
        playing = true;
        if (restart || !same) {
            elapsedMillis = 0;
        }
    }

    public String getTag() {
        return tag;
    }

    public void pause() {
        playing = false;
    }

    public void resume() {
        playing = true;
    }

    public boolean isPlaying() {
        return playing;
    }

    /** Stops and goes back to the first frame of the tag. */
    public void stop() {
        playing = false;
        elapsedMillis = 0;
    }

    public float getSpeed() {
        return speed;
    }

    /** Playback speed factor; 0 holds the current frame, 1 is the authored speed. */
    public void setSpeed(float speed) {
        if (speed < 0f || Float.isNaN(speed)) {
            throw new IllegalArgumentException("speed cannot be negative or NaN: " + speed);
        }
        this.speed = speed;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    /** Moves time forward by {@code seconds} (when playing). */
    public void advance(float seconds) {
        if (seconds < 0f) {
            throw new IllegalArgumentException("seconds cannot be negative: " + seconds);
        }
        if (playing) {
            elapsedMillis += seconds * speed * MILLIS_PER_SECOND;
        }
    }

    /** The frame to draw now. */
    public int getFrame() {
        return SpriteAnimation.frameAt(doc, find(tag), (long) elapsedMillis, loop);
    }

    /** Whether a non-looping animation has reached its end (always false when looping). */
    public boolean isFinished() {
        if (loop) {
            return false;
        }
        long total = 0;
        for (int frame : SpriteAnimation.sequence(doc, find(tag))) {
            total += doc.getFrameDuration(frame);
        }
        return elapsedMillis >= total;
    }
}
