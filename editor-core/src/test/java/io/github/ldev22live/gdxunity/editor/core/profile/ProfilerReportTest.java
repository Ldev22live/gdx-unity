package io.github.ldev22live.gdxunity.editor.core.profile;

import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M9.6: one frame's breakdown as the Profiler panel lists it. */
class ProfilerReportTest {
    private static final long MS = 1_000_000L;

    private long now;
    private long draws;
    private final FrameProfiler profiler = new FrameProfiler(10, () -> now);
    private final int play = profiler.section("Play", FrameProfiler.ROOT);
    private final int physics = profiler.section("3D physics", play);
    private final int sceneUpdate = profiler.section("Scene update", play);
    private final int ui = profiler.section("Editor UI", FrameProfiler.ROOT);

    ProfilerReportTest() {
        profiler.setRecording(true);
        profiler.setCounterSource(new FrameProfiler.CounterSource() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public String name(int counter) {
                return "Draw calls";
            }

            @Override
            public long read(int counter) {
                return draws;
            }
        });
    }

    private void timed(int section, long millis) {
        profiler.begin(section);
        now += millis * MS;
        profiler.end(section);
    }

    /** A frame: Play 6 ms (physics 1, scene update 3, 2 untimed), UI {@code uiMillis} with 30 draw calls, 1 ms outside it all. */
    private void frame(long uiMillis) {
        profiler.beginFrame();
        profiler.begin(play);
        timed(physics, 1);
        timed(sceneUpdate, 3);
        now += 2 * MS;
        profiler.end(play);
        profiler.begin(ui);
        draws += 30;
        now += uiMillis * MS;
        profiler.end(ui);
        now += MS;
        profiler.endFrame();
        now += 5 * MS; // waiting for the next frame
    }

    @Test
    void shouldListTheTreeSlowestFirstWithTheRemaindersSoEachLevelAddsUp() {
        frame(2);

        List<ProfilerReport.Row> rows = ProfilerReport.rows(profiler, 0, 60);

        assertEquals(List.of("Play", "Scene update", "3D physics", "Other", "Editor UI", "Untimed"),
            rows.stream().map(ProfilerReport.Row::name).toList());
        assertEquals(List.of(0, 1, 1, 1, 0, 0), rows.stream().map(ProfilerReport.Row::depth).toList());
        assertEquals(List.of(6.0, 3.0, 1.0, 2.0, 2.0, 1.0), rows.stream().map(ProfilerReport.Row::millis).toList());
        assertEquals(66.67, rows.get(0).percent(), 0.01, "6 of the frame's 9 ms");
        assertTrue(rows.get(3).isRemainder());
        assertArrayEquals(new long[] {30}, rows.get(4).counts());
        assertArrayEquals(new long[0], rows.get(5).counts());
    }

    @Test
    void shouldAverageOverRecentFramesAndSumUpTheFrame() {
        frame(2);
        frame(8);

        ProfilerReport.Row uiRow = ProfilerReport.rows(profiler, 0, 60).get(0);
        assertEquals("Editor UI", uiRow.name(), "now the slowest top-level section");
        assertEquals(8.0, uiRow.millis(), 1e-9);
        assertEquals(5.0, uiRow.averageMillis(), 1e-9);

        ProfilerReport.Summary newest = ProfilerReport.summary(profiler, 0, 60);
        assertEquals(15.0, newest.frameMillis(), 1e-9);
        assertEquals(0.0, newest.periodMillis(), "no frame after it yet");
        assertEquals(0.0, newest.framesPerSecond());
        assertEquals(12.0, newest.averageMillis(), 1e-9);
        assertEquals(15.0, newest.slowestMillis(), 1e-9);

        ProfilerReport.Summary older = ProfilerReport.summary(profiler, 1, 1);
        assertEquals(9.0, older.frameMillis(), 1e-9);
        assertEquals(14.0, older.periodMillis(), 1e-9, "its 9 ms of work and 5 ms of waiting");
        assertEquals(1000.0 / 14.0, older.framesPerSecond(), 1e-9);
        assertEquals(5.0, older.waitingMillis(), 1e-9);
        assertEquals(15.0, older.averageMillis(), 1e-9, "the average is always over the newest frames");
    }

    @Test
    void shouldKeepASectionThatSkippedTheShownFrameAndHideOneNeverTimed() {
        int navigation = profiler.section("Navigation bake", FrameProfiler.ROOT);
        profiler.section("Never used", FrameProfiler.ROOT);
        profiler.beginFrame();
        timed(navigation, 4);
        profiler.endFrame();
        frame(2);

        List<String> names = ProfilerReport.rows(profiler, 0, 60).stream().map(ProfilerReport.Row::name).toList();
        assertTrue(names.contains("Navigation bake"), names.toString());
        assertFalse(names.contains("Never used"), names.toString());
        assertFalse(ProfilerReport.rows(profiler, 0, 1).stream().anyMatch(row -> row.name().equals("Navigation bake")),
            "not timed in the one frame averaged");
    }

    @Test
    void shouldPrintTheFrameAsText() {
        int entities = profiler.value("Entities");
        profiler.beginFrame();
        profiler.set(entities, 42);
        timed(play, 2);
        profiler.endFrame();

        String text = ProfilerReport.toText(profiler, 0, 60);

        assertTrue(text.startsWith("Frame 0 back (of 1): 2.00 ms work, next frame not started; average 2.00 ms, slowest 2.00 ms; "
            + "Entities 42\n"), text);
        assertTrue(text.contains("  Play 2.000 ms (100.0%, average 2.000 ms, Draw calls 0)\n"), text);
    }

    @Test
    void shouldRejectAFrameNotInTheHistory() {
        assertThrows(IllegalArgumentException.class, () -> ProfilerReport.rows(profiler, 0, 60));
        frame(1);
        assertThrows(IllegalArgumentException.class, () -> ProfilerReport.summary(profiler, 0, 0));
    }
}
