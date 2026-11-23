package io.github.ldev22live.gdxunity.engine.audio;

import java.util.List;

/** An echo with feedback. Parameters: {@code time} (seconds, 0.02..1), {@code feedback} (0..0.95), {@code mix} (0..1). */
public final class DelayEffect implements Effect {
    /** Longest delay time in seconds. */
    public static final float MAX_TIME = 1f;
    /** Shortest delay time in seconds. */
    public static final float MIN_TIME = 0.02f;
    /** Feedback above this would never die away. */
    public static final float MAX_FEEDBACK = 0.95f;
    private static final List<String> NAMES = List.of("time", "feedback", "mix");

    private final float sampleRate;
    private final float[] bufferLeft;
    private final float[] bufferRight;
    private int writeIndex;
    private float time = 0.3f;
    private float feedback = 0.4f;
    private float mix = 0.3f;

    public DelayEffect(float sampleRate) {
        if (sampleRate <= 0f) {
            throw new IllegalArgumentException("sampleRate must be positive: " + sampleRate);
        }
        this.sampleRate = sampleRate;
        int size = (int) (MAX_TIME * sampleRate) + 1;
        bufferLeft = new float[size];
        bufferRight = new float[size];
    }

    @Override
    public String getType() {
        return "delay";
    }

    @Override
    public List<String> getParameterNames() {
        return NAMES;
    }

    @Override
    public float getParameter(String name) {
        return switch (name) {
            case "time" -> time;
            case "feedback" -> feedback;
            case "mix" -> mix;
            default -> throw new IllegalArgumentException("Unknown delay parameter: " + name);
        };
    }

    @Override
    public void setParameter(String name, float value) {
        switch (name) {
            case "time" -> time = Math.min(Math.max(value, MIN_TIME), MAX_TIME);
            case "feedback" -> feedback = Math.min(Math.max(value, 0f), MAX_FEEDBACK);
            case "mix" -> mix = Math.min(Math.max(value, 0f), 1f);
            default -> throw new IllegalArgumentException("Unknown delay parameter: " + name);
        }
    }

    @Override
    public void process(float[] left, float[] right, int offset, int frames) {
        int size = bufferLeft.length;
        int delayFrames = Math.min((int) (time * sampleRate), size - 1);
        for (int i = 0; i < frames; i++) {
            int read = (writeIndex - delayFrames + size) % size;
            float echoLeft = bufferLeft[read];
            float echoRight = bufferRight[read];
            float inLeft = left[offset + i];
            float inRight = right[offset + i];
            // Ping-pong: each side feeds the other, so the echoes move across the stereo field
            bufferLeft[writeIndex] = inLeft + echoRight * feedback;
            bufferRight[writeIndex] = inRight + echoLeft * feedback;
            left[offset + i] = inLeft + echoLeft * mix;
            right[offset + i] = inRight + echoRight * mix;
            writeIndex = (writeIndex + 1) % size;
        }
    }

    @Override
    public void reset() {
        java.util.Arrays.fill(bufferLeft, 0f);
        java.util.Arrays.fill(bufferRight, 0f);
        writeIndex = 0;
    }
}
