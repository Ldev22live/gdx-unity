package io.github.ldev22live.gdxunity.engine.core.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameClockTest {

    @Test
    void shouldAcceptValidDeltaValuesWhenClockTicks() {
        FrameClock clock = new FrameClock(() -> 0.016f); // ~60fps
        float dt = clock.tick();

        assertEquals(0.016f, dt, 0.0001f);
        assertEquals(0.016f, clock.getDeltaTime(), 0.0001f);
        assertEquals(0.016f, clock.getTotalElapsedTime(), 0.0001f);
        assertEquals(1L, clock.getFrameCount());
    }

    @Test
    void shouldClampExcessiveDeltaValuesWhenFrameSpikesOccur() {
        FrameClock clock = new FrameClock(() -> 0.5f, 0.1f);
        float dt = clock.tick();

        assertEquals(0.1f, dt, 0.0001f);
        assertEquals(0.1f, clock.getDeltaTime(), 0.0001f);
        assertEquals(0.1f, clock.getTotalElapsedTime(), 0.0001f);
    }

    @Test
    void shouldSanitizeNegativeValuesWhenDeltaIsNegative() {
        FrameClock clock = new FrameClock();
        float dt = clock.tick(-0.05f);

        assertEquals(0.0f, dt, 0.0001f);
        assertEquals(0.0f, clock.getDeltaTime(), 0.0001f);
        assertEquals(0.0f, clock.getTotalElapsedTime(), 0.0001f);
    }

    @Test
    void shouldSanitizeNaNAndInfiniteValuesWhenInvalidDeltaSupplied() {
        FrameClock clock = new FrameClock();

        assertEquals(0.0f, clock.tick(Float.NaN));
        assertEquals(0.0f, clock.tick(Float.POSITIVE_INFINITY));
        assertEquals(0.0f, clock.tick(Float.NEGATIVE_INFINITY));
        assertEquals(0.0f, clock.getTotalElapsedTime());
    }

    @Test
    void shouldAdvanceElapsedTimeAccuratelyAcrossMultipleTicks() {
        float[] sampleFrames = {0.010f, 0.015f, 0.020f};
        FrameClock clock = new FrameClock();

        for (float sample : sampleFrames) {
            clock.tick(sample);
        }

        assertEquals(3L, clock.getFrameCount());
        assertEquals(0.045f, clock.getTotalElapsedTime(), 0.0001f);
        assertEquals(0.020f, clock.getDeltaTime(), 0.0001f);
    }

    @Test
    void shouldRejectInvalidMaxDeltaTimeConfiguration() {
        FrameClock clock = new FrameClock();

        assertThrows(IllegalArgumentException.class, () -> clock.setMaxDeltaTime(0.0f));
        assertThrows(IllegalArgumentException.class, () -> clock.setMaxDeltaTime(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> clock.setMaxDeltaTime(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> clock.setMaxDeltaTime(Float.POSITIVE_INFINITY));
    }
}
