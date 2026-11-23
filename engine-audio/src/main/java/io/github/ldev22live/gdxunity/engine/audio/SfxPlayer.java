package io.github.ldev22live.gdxunity.engine.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mixes short sound effects (sfxr presets, TD-043) into a stereo stream. {@link #play} may be called from the game
 * thread while another thread calls {@link #render}; the two share only a lock-free queue. Headless: the caller feeds
 * the rendered blocks to a sound device.
 *
 * <p>Each effect is generated once ({@link #prepare}) and then replayed from memory at the requested volume and pitch. A recorded
 * clip (a {@code .wav} read by {@link WavIO}) is added with {@link #addClip} and played by the name it was added under, with
 * an optional stereo pan (Phase 20-C).
 * Overlapping effects add up; when {@link #MAX_VOICES} are sounding the oldest is cut.
 */
public final class SfxPlayer {
    /** Output sample rate, Hz. */
    public static final int SAMPLE_RATE = SfxrGenerator.SAMPLE_RATE;
    /** Most effects sounding at once. */
    public static final int MAX_VOICES = 16;

    private final Map<String, SampleData> sounds = new ConcurrentHashMap<>();
    private final Queue<Voice> pending = new ConcurrentLinkedQueue<>();
    private final List<Voice> active = new ArrayList<>(MAX_VOICES);

    /** Whether {@code sound} names a factory effect this player can make. */
    public static boolean isKnown(String sound) {
        return sound != null && SfxrParams.presetNames().contains(sound);
    }

    /**
     * Adds a recorded clip, played by {@code name} from now on. A name that is a factory effect cannot be used, so a clip never
     * hides a preset.
     *
     * @throws IllegalArgumentException if {@code name} is blank or a factory effect name
     */
    public void addClip(String name, SampleData clip) {
        Objects.requireNonNull(clip, "clip cannot be null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A clip needs a name");
        }
        if (isKnown(name)) {
            throw new IllegalArgumentException("'" + name + "' is the name of a factory effect");
        }
        sounds.put(name, clip);
    }

    /** Whether {@code sound} is a factory effect or a clip added with {@link #addClip}. */
    public boolean isAvailable(String sound) {
        return isKnown(sound) || (sound != null && sounds.containsKey(sound));
    }

    /**
     * Generates {@code sound} now, so the first {@link #play} does not pay for it.
     *
     * @throws IllegalArgumentException if {@code sound} is not a factory effect name
     */
    public void prepare(String sound) {
        Objects.requireNonNull(sound, "sound cannot be null");
        if (sounds.containsKey(sound)) {
            return;
        }
        if (!isKnown(sound)) {
            throw new IllegalArgumentException("Unknown sound '" + sound + "'; expected one of " + SfxrParams.presetNames());
        }
        sounds.computeIfAbsent(sound, name -> SfxrGenerator.generate(SfxrParams.preset(name)));
    }

    /**
     * Starts an effect; the next {@link #render} call mixes it in.
     *
     * @param sound  a factory effect name
     * @param volume linear gain, 0 to 1
     * @param pitch  speed multiplier, above 0
     * @throws IllegalArgumentException for an unknown sound, or a volume or pitch outside its range
     */
    public void play(String sound, float volume, float pitch) {
        play(sound, volume, pitch, 0f);
    }

    /**
     * Starts an effect placed between the speakers.
     *
     * @param pan -1 is all in the left speaker, 0 in the middle (both at full volume), 1 all in the right; the speaker the sound is
     *            moving away from gets quieter in proportion
     * @throws IllegalArgumentException for an unknown sound, or a volume, pitch or pan outside its range
     */
    public void play(String sound, float volume, float pitch, float pan) {
        if (!(pan >= -1f && pan <= 1f)) {
            throw new IllegalArgumentException("pan must be within -1 to 1: " + pan);
        }
        if (!(volume >= 0f && volume <= 1f)) {
            throw new IllegalArgumentException("volume must be within 0 to 1: " + volume);
        }
        if (!(pitch > 0f) || Float.isInfinite(pitch)) {
            throw new IllegalArgumentException("pitch must be above 0: " + pitch);
        }
        prepare(sound);
        pending.add(new Voice(sounds.get(sound), volume, pitch, pan));
    }

    /** Number of effects currently sounding, as of the last {@link #render}. */
    public int getActiveVoices() {
        return active.size();
    }

    /**
     * Mixes the next {@code frames} frames into {@code left} and {@code right}, overwriting them. Call from one
     * thread only (the sound output thread).
     */
    public void render(float[] left, float[] right, int frames) {
        Objects.requireNonNull(left, "left cannot be null");
        Objects.requireNonNull(right, "right cannot be null");
        if (frames < 0 || frames > left.length || frames > right.length) {
            throw new IllegalArgumentException("frames out of range: " + frames);
        }
        for (Voice voice = pending.poll(); voice != null; voice = pending.poll()) {
            if (active.size() == MAX_VOICES) {
                active.remove(0);
            }
            active.add(voice);
        }
        for (int i = 0; i < frames; i++) {
            left[i] = 0f;
            right[i] = 0f;
        }
        for (int v = active.size() - 1; v >= 0; v--) {
            Voice voice = active.get(v);
            voice.mixInto(left, right, frames);
            if (voice.finished()) {
                active.remove(v);
            }
        }
    }

    /** One playing effect: a position in a sound, advanced by the pitch each frame. */
    private static final class Voice {
        private final SampleData data;
        private final float volumeLeft;
        private final float volumeRight;
        private final double step;
        private double position;

        Voice(SampleData data, float volume, float pitch, float pan) {
            this.data = data;
            // Panning only quietens the far speaker, so a sound in the middle is as loud as it always was
            this.volumeLeft = volume * (pan > 0f ? 1f - pan : 1f);
            this.volumeRight = volume * (pan < 0f ? 1f + pan : 1f);
            this.step = (double) pitch * data.getSampleRate() / SAMPLE_RATE;
        }

        boolean finished() {
            return position >= data.getLength() - 1;
        }

        void mixInto(float[] left, float[] right, int frames) {
            float[] source = data.getLeft();
            float[] sourceRight = data.getRight();
            int last = data.getLength() - 1;
            for (int i = 0; i < frames && position < last; i++) {
                int index = (int) position;
                float fraction = (float) (position - index);
                left[i] += volumeLeft * (source[index] + (source[index + 1] - source[index]) * fraction);
                right[i] += volumeRight * (sourceRight[index] + (sourceRight[index + 1] - sourceRight[index]) * fraction);
                position += step;
            }
        }
    }
}
