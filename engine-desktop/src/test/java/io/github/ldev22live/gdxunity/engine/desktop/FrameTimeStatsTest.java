package io.github.ldev22live.gdxunity.engine.desktop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M9.5: frame times kept per frame, so slow frames show in the percentiles. */
class FrameTimeStatsTest {
    private static final long MS = 1_000_000L;

    @Test
    void shouldReportMeanMedianPercentileAndMax() {
        FrameTimeStats stats = new FrameTimeStats(100);
        // 95 frames of 10 ms and 5 hitches of 50 ms: a mean of 12 ms hides them, the 99th percentile does not
        for (int i = 0; i < 95; i++) {
            stats.add(10 * MS);
        }
        for (int i = 0; i < 5; i++) {
            stats.add(50 * MS);
        }

        assertEquals(100, stats.count());
        assertEquals(12.0, stats.meanMillis(), 1e-9);
        assertEquals(10.0, stats.percentileMillis(50), 1e-9);
        assertEquals(10.0, stats.percentileMillis(95), 1e-9);
        assertEquals(50.0, stats.percentileMillis(96), 1e-9);
        assertEquals(50.0, stats.maxMillis(), 1e-9);
    }

    @Test
    void shouldUseTheNearestRankInAnyOrder() {
        FrameTimeStats stats = new FrameTimeStats(4);
        stats.add(4 * MS);
        stats.add(1 * MS);
        stats.add(3 * MS);
        stats.add(2 * MS);

        assertEquals(2.0, stats.percentileMillis(50), 1e-9);
        assertEquals(4.0, stats.percentileMillis(95), 1e-9);
        assertEquals(1.0, stats.percentileMillis(1), 1e-9);
    }

    @Test
    void shouldCountFramesPastTheCapacityInTheMeanAndMaxButNotThePercentiles() {
        FrameTimeStats stats = new FrameTimeStats(2);
        stats.add(1 * MS);
        stats.add(1 * MS);
        stats.add(7 * MS);

        assertEquals(3, stats.count());
        assertEquals(1, stats.dropped());
        assertEquals(3.0, stats.meanMillis(), 1e-9);
        assertEquals(7.0, stats.maxMillis(), 1e-9);
        assertEquals(1.0, stats.percentileMillis(95), 1e-9);
    }

    @Test
    void shouldGiveNaNWithoutFrames() {
        FrameTimeStats stats = new FrameTimeStats(1);

        assertTrue(Double.isNaN(stats.meanMillis()));
        assertTrue(Double.isNaN(stats.maxMillis()));
        assertTrue(Double.isNaN(stats.percentileMillis(50)));
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new FrameTimeStats(0));
        FrameTimeStats stats = new FrameTimeStats(1);
        assertThrows(IllegalArgumentException.class, () -> stats.add(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.percentileMillis(0));
        assertThrows(IllegalArgumentException.class, () -> stats.percentileMillis(101));
    }
}
