package io.github.ldev22live.gdxunity.engine.audio.song;

/**
 * One note in a pattern.
 *
 * @param tick     start, in ticks from the pattern start ({@link Song#TICKS_PER_BEAT} ticks per beat)
 * @param length   duration in ticks, at least 1
 * @param pitch    MIDI note number 0..127 (60 = middle C)
 * @param velocity loudness 0..1
 */
public record Note(int tick, int length, int pitch, float velocity) {
    /** Lowest and highest pitch a note can have. */
    public static final int MIN_PITCH = 0;
    public static final int MAX_PITCH = 127;

    public Note {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must not be negative: " + tick);
        }
        if (length < 1) {
            throw new IllegalArgumentException("length must be at least 1 tick: " + length);
        }
        if (pitch < MIN_PITCH || pitch > MAX_PITCH) {
            throw new IllegalArgumentException("pitch must be 0..127: " + pitch);
        }
        if (!(velocity >= 0f && velocity <= 1f)) {
            throw new IllegalArgumentException("velocity must be 0..1: " + velocity);
        }
    }

    /** First tick after the note. */
    public int endTick() {
        return tick + length;
    }

    public Note withTick(int newTick) {
        return new Note(newTick, length, pitch, velocity);
    }

    public Note withLength(int newLength) {
        return new Note(tick, newLength, pitch, velocity);
    }

    public Note withPitch(int newPitch) {
        return new Note(tick, length, newPitch, velocity);
    }

    public Note withVelocity(float newVelocity) {
        return new Note(tick, length, pitch, newVelocity);
    }

    /** Name like "C4" or "F#3" for a MIDI pitch (60 is C4). */
    public static String pitchName(int pitch) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        return names[Math.floorMod(pitch, 12)] + (Math.floorDiv(pitch, 12) - 1);
    }

    /** True for the black keys of a piano. */
    public static boolean isBlackKey(int pitch) {
        return switch (Math.floorMod(pitch, 12)) {
            case 1, 3, 6, 8, 10 -> true;
            default -> false;
        };
    }
}
