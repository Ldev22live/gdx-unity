package io.github.ldev22live.gdxunity.engine.audio;

/** Attack-decay-sustain-release envelope: linear attack and decay, exponential release. */
public final class Adsr {
    /** Stages of the envelope. */
    public enum Stage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

    /** The release is over when the level falls under this. */
    static final float SILENT = 0.0001f;
    /** Smallest stage time in seconds, so no stage divides by zero. */
    static final float MIN_SECONDS = 0.001f;
    /** The release curve falls by this factor (-80 dB) over the release time. */
    private static final double RELEASE_FALL = 10000.0;

    private final float sampleRate;
    private float attack = 0.01f;
    private float decay = 0.1f;
    private float sustain = 0.7f;
    private float release = 0.2f;
    private Stage stage = Stage.IDLE;
    private float level;
    private float releaseCoefficient;

    public Adsr(float sampleRate) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
    }

    /** Sets the times in seconds and the sustain level (0..1). */
    public void set(float attackSeconds, float decaySeconds, float sustainLevel, float releaseSeconds) {
        this.attack = Math.max(attackSeconds, MIN_SECONDS);
        this.decay = Math.max(decaySeconds, MIN_SECONDS);
        this.sustain = Math.min(Math.max(sustainLevel, 0f), 1f);
        this.release = Math.max(releaseSeconds, MIN_SECONDS);
    }

    /** Starts the attack from the current level, so a retrigger does not click. */
    public void noteOn() {
        stage = Stage.ATTACK;
    }

    /** Starts the release from the current level. */
    public void noteOff() {
        if (stage == Stage.IDLE || stage == Stage.RELEASE) {
            return;
        }
        stage = Stage.RELEASE;
        releaseCoefficient = (float) Math.exp(-Math.log(RELEASE_FALL) / (release * sampleRate));
    }

    /** True once the release has faded out (or the envelope never started). */
    public boolean isFinished() {
        return stage == Stage.IDLE;
    }

    public Stage getStage() {
        return stage;
    }

    public float getLevel() {
        return level;
    }

    /** Level for the next sample, 0..1. */
    public float next() {
        switch (stage) {
            case IDLE -> { }
            case ATTACK -> {
                level += 1f / (attack * sampleRate);
                if (level >= 1f) {
                    level = 1f;
                    stage = Stage.DECAY;
                }
            }
            case DECAY -> {
                level -= (1f - sustain) / (decay * sampleRate);
                if (level <= sustain) {
                    level = sustain;
                    stage = Stage.SUSTAIN;
                }
            }
            case SUSTAIN -> level = sustain;
            case RELEASE -> {
                level *= releaseCoefficient;
                if (level < SILENT) {
                    level = 0f;
                    stage = Stage.IDLE;
                }
            }
            default -> throw new IllegalStateException("Unknown stage " + stage);
        }
        return level;
    }

    /** Back to silence with no release. */
    public void reset() {
        stage = Stage.IDLE;
        level = 0f;
    }
}
