package io.github.ldev22live.gdxunity.engine.audio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Mixes a fixed number of channel strips into a master strip. Each strip has volume, pan, mute, solo, up to
 * {@link #MAX_EFFECTS} effects in series and peak meters. Strips are fed by a {@link StripSource}.
 */
public final class Mixer {
    /** Strips a new mixer has, besides the master. */
    public static final int DEFAULT_STRIPS = 8;
    /** Effects one strip can hold. */
    public static final int MAX_EFFECTS = 4;
    /** Highest fader value (a little above unity for boosting quiet instruments). */
    public static final float MAX_VOLUME = 1.5f;
    /** Levels below this pass the master limiter untouched. */
    public static final float LIMITER_THRESHOLD = 0.8f;
    /** How fast a meter falls, per second, as a share of its level. */
    private static final float METER_FALL_PER_SECOND = 3f;

    /** Supplies the sound that goes into a strip. */
    public interface StripSource {
        /** Adds the strip's sound into the two buffers (which start silent). */
        void renderStrip(int stripIndex, float[] left, float[] right, int offset, int frames);
    }

    /** One mixer channel (or the master). */
    public static final class Strip {
        private final float sampleRate;
        private final List<Effect> effects = new ArrayList<>();
        private String name;
        private float volume = 0.8f;
        private float pan;
        private boolean muted;
        private boolean solo;
        private volatile float peakLeft;
        private volatile float peakRight;

        Strip(String name, float sampleRate) {
            this.name = name;
            this.sampleRate = sampleRate;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
        }

        public float getVolume() {
            return volume;
        }

        public void setVolume(float volume) {
            this.volume = Math.min(Math.max(volume, 0f), MAX_VOLUME);
        }

        public float getPan() {
            return pan;
        }

        /** -1 is full left, 0 centre, 1 full right. */
        public void setPan(float pan) {
            this.pan = Math.min(Math.max(pan, -1f), 1f);
        }

        public boolean isMuted() {
            return muted;
        }

        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        public boolean isSolo() {
            return solo;
        }

        public void setSolo(boolean solo) {
            this.solo = solo;
        }

        /** The effects in processing order; the list is a read-only view. */
        public List<Effect> getEffects() {
            return Collections.unmodifiableList(effects);
        }

        /** Adds an effect at the end; fails when the strip is full. */
        public synchronized void addEffect(Effect effect) {
            Objects.requireNonNull(effect, "effect cannot be null");
            if (effects.size() >= MAX_EFFECTS) {
                throw new IllegalStateException("A strip holds at most " + MAX_EFFECTS + " effects");
            }
            effects.add(effect);
        }

        /** Removes an effect; returns false when it was not on this strip. */
        public synchronized boolean removeEffect(Effect effect) {
            return effects.remove(effect);
        }

        /** Recent peak of the left side after volume and pan, 0..1+. */
        public float getPeakLeft() {
            return peakLeft;
        }

        public float getPeakRight() {
            return peakRight;
        }

        synchronized void processEffects(float[] left, float[] right, int frames) {
            for (Effect effect : effects) {
                effect.process(left, right, 0, frames);
            }
        }

        void meter(float left, float right, int frames) {
            float fall = Math.max(0f, 1f - METER_FALL_PER_SECOND * frames / sampleRate);
            peakLeft = Math.max(left, peakLeft * fall);
            peakRight = Math.max(right, peakRight * fall);
        }
    }

    private final float sampleRate;
    private final List<Strip> strips = new ArrayList<>();
    private final Strip master;
    private float[] stripLeft = new float[0];
    private float[] stripRight = new float[0];
    private float[] sumLeft = new float[0];
    private float[] sumRight = new float[0];

    public Mixer(float sampleRate) {
        this(sampleRate, DEFAULT_STRIPS);
    }

    public Mixer(float sampleRate, int stripCount) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        if (stripCount < 1) {
            throw new IllegalArgumentException("stripCount must be at least 1: " + stripCount);
        }
        this.sampleRate = sampleRate;
        for (int i = 0; i < stripCount; i++) {
            strips.add(new Strip("Insert " + (i + 1), sampleRate));
        }
        master = new Strip("Master", sampleRate);
        master.setVolume(1f);
    }

    public int getStripCount() {
        return strips.size();
    }

    public Strip getStrip(int index) {
        if (index < 0 || index >= strips.size()) {
            throw new IndexOutOfBoundsException("Mixer strip " + index + " of " + strips.size());
        }
        return strips.get(index);
    }

    public Strip getMaster() {
        return master;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    /**
     * Renders {@code frames} frames of the whole mix into {@code left} and {@code right} from {@code offset}
     * (overwritten).
     * Allocates only when {@code frames} is larger than any call before.
     */
    public void render(StripSource source, float[] left, float[] right, int offset, int frames) {
        Objects.requireNonNull(source, "source cannot be null");
        if (stripLeft.length < frames) {
            stripLeft = new float[frames];
            stripRight = new float[frames];
            sumLeft = new float[frames];
            sumRight = new float[frames];
        }
        Arrays.fill(sumLeft, 0, frames, 0f);
        Arrays.fill(sumRight, 0, frames, 0f);
        boolean anySolo = false;
        for (Strip strip : strips) {
            anySolo |= strip.isSolo();
        }
        for (int s = 0; s < strips.size(); s++) {
            Strip strip = strips.get(s);
            Arrays.fill(stripLeft, 0, frames, 0f);
            Arrays.fill(stripRight, 0, frames, 0f);
            // Muted and unsoloed strips still run, so their instruments and effects keep their state
            source.renderStrip(s, stripLeft, stripRight, 0, frames);
            strip.processEffects(stripLeft, stripRight, frames);
            boolean audible = !strip.isMuted() && (!anySolo || strip.isSolo());
            addToSum(strip, audible, frames);
        }
        master.processEffects(sumLeft, sumRight, frames);
        float peakLeft = 0f;
        float peakRight = 0f;
        float gain = master.isMuted() ? 0f : master.getVolume();
        for (int i = 0; i < frames; i++) {
            float l = sumLeft[i] * gain;
            float r = sumRight[i] * gain;
            left[offset + i] = softLimit(l);
            right[offset + i] = softLimit(r);
            peakLeft = Math.max(peakLeft, Math.abs(l));
            peakRight = Math.max(peakRight, Math.abs(r));
        }
        master.meter(peakLeft, peakRight, frames);
    }

    /**
     * Keeps the output inside -1..1 without hard clipping: unchanged up to {@link #LIMITER_THRESHOLD}, then bent
     * smoothly towards full scale, so a loud mix gets denser instead of harsh. The meters still show the true level.
     */
    public static float softLimit(float x) {
        float magnitude = Math.abs(x);
        if (magnitude <= LIMITER_THRESHOLD) {
            return x;
        }
        float room = 1f - LIMITER_THRESHOLD;
        float limited = LIMITER_THRESHOLD + room * (float) Math.tanh((magnitude - LIMITER_THRESHOLD) / room);
        return Math.copySign(limited, x);
    }

    private void addToSum(Strip strip, boolean audible, int frames) {
        // Constant-power pan: equal loudness anywhere across the field
        double angle = (strip.getPan() + 1f) * Math.PI / 4.0;
        float gainLeft = (float) (Math.cos(angle) * Math.sqrt(2.0)) * strip.getVolume();
        float gainRight = (float) (Math.sin(angle) * Math.sqrt(2.0)) * strip.getVolume();
        float peakLeft = 0f;
        float peakRight = 0f;
        for (int i = 0; i < frames; i++) {
            float l = stripLeft[i] * gainLeft;
            float r = stripRight[i] * gainRight;
            peakLeft = Math.max(peakLeft, Math.abs(l));
            peakRight = Math.max(peakRight, Math.abs(r));
            if (audible) {
                sumLeft[i] += l;
                sumRight[i] += r;
            }
        }
        strip.meter(peakLeft, peakRight, frames);
    }
}
