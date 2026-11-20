package io.github.ldev22live.gdxunity.engine.core.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Roadmap M9.6: a frame's time split into named sections, kept for recent frames. */
class FrameProfilerTest {
    private static final long MS = 1_000_000L;

    /** A clock the test moves by hand. */
    private static final class Clock {
        long now;

        void advance(long millis) {
            now += millis * MS;
        }
    }

    /** Draw calls the test adds by hand. */
    private static final class Draws implements FrameProfiler.CounterSource {
        long draws;
        long vertices;
        int framesStarted;

        @Override
        public int size() {
            return 2;
        }

        @Override
        public String name(int counter) {
            return counter == 0 ? "Draw calls" : "Vertices";
        }

        @Override
        public long read(int counter) {
            return counter == 0 ? draws : vertices;
        }

        @Override
        public void frameStarted() {
            framesStarted++;
            draws = 0;
            vertices = 0;
        }
    }

    private final Clock clock = new Clock();

    private FrameProfiler recording(int history) {
        FrameProfiler profiler = new FrameProfiler(history, () -> clock.now);
        profiler.setRecording(true);
        return profiler;
    }

    @Test
    void shouldTimeNestedSectionsAndAddUpASectionTimedTwiceInOneFrame() {
        FrameProfiler profiler = recording(10);
        int play = profiler.section("Play", FrameProfiler.ROOT);
        int physics = profiler.section("3D physics", play);
        int render = profiler.section("Render", FrameProfiler.ROOT);

        profiler.beginFrame();
        profiler.begin(play);
        clock.advance(1);
        profiler.begin(physics);
        clock.advance(3);
        profiler.end(physics);
        clock.advance(1);
        profiler.end(play);
        // Two viewport panes: the render section is timed twice
        for (int pane = 0; pane < 2; pane++) {
            profiler.begin(render);
            clock.advance(2);
            profiler.end(render);
        }
        clock.advance(1);
        profiler.endFrame();

        assertEquals(1, profiler.getFrameCount());
        assertEquals(10 * MS, profiler.getFrameNanos(0));
        assertEquals(5 * MS, profiler.getSectionNanos(0, play));
        assertEquals(3 * MS, profiler.getSectionNanos(0, physics));
        assertEquals(4 * MS, profiler.getSectionNanos(0, render));
        assertEquals(2, profiler.getSectionCalls(0, render));
        assertEquals(1, profiler.getSectionDepth(physics));
        assertEquals(play, profiler.getSectionParent(physics));
    }

    @Test
    void shouldRegisterASectionOncePerParent() {
        FrameProfiler profiler = recording(1);
        int play = profiler.section("Play", FrameProfiler.ROOT);
        int edit = profiler.section("Edit", FrameProfiler.ROOT);

        assertEquals(play, profiler.section("Play", FrameProfiler.ROOT));
        assertTrue(profiler.section("Scene update", play) != profiler.section("Scene update", edit), "same name, other parent");
        assertEquals(4, profiler.getSectionCount());
        assertThrows(IllegalArgumentException.class, () -> profiler.section("Orphan", 99));
    }

    @Test
    void shouldKeepTheLastFramesAndCountBackFromTheNewest() {
        FrameProfiler profiler = recording(3);
        int work = profiler.section("Work", FrameProfiler.ROOT);
        for (int frame = 1; frame <= 5; frame++) {
            profiler.beginFrame();
            profiler.begin(work);
            clock.advance(frame);
            profiler.end(work);
            profiler.endFrame();
            clock.advance(10);
        }

        assertEquals(3, profiler.getFrameCount());
        assertEquals(5, profiler.getRecordedFrames());
        assertEquals(5 * MS, profiler.getSectionNanos(0, work));
        assertEquals(4 * MS, profiler.getSectionNanos(1, work));
        assertEquals(3 * MS, profiler.getSectionNanos(2, work));
        assertEquals(14 * MS, profiler.getPeriodNanos(1), "from frame 4's start to frame 5's: 4 ms of work and 10 ms between");
        assertEquals(0L, profiler.getPeriodNanos(0), "the newest frame's period ends when the next frame starts");
        profiler.beginFrame();
        assertEquals(15 * MS, profiler.getPeriodNanos(0), "known during the next frame, when a panel reads it");
        profiler.endFrame();
        assertThrows(IllegalArgumentException.class, () -> profiler.getFrameNanos(3));
    }

    @Test
    void shouldReadOnlyFinishedFramesWhileAFrameIsRecorded() {
        // The panel draws during the frame it is part of: it must see the finished frames, not the one being filled
        FrameProfiler profiler = recording(2);
        int work = profiler.section("Work", FrameProfiler.ROOT);
        for (int frame = 1; frame <= 2; frame++) {
            profiler.beginFrame();
            profiler.begin(work);
            clock.advance(frame);
            profiler.end(work);
            profiler.endFrame();
        }

        profiler.beginFrame();
        assertEquals(1, profiler.getFrameCount(), "the frame being recorded took the oldest one's place");
        assertEquals(2 * MS, profiler.getSectionNanos(0, work));
        profiler.endFrame();
        assertEquals(2, profiler.getFrameCount());
        assertEquals(0L, profiler.getSectionNanos(0, work), "the new frame timed nothing");
    }

    @Test
    void shouldRecordNothingWhenNotRecordingOrPaused() {
        FrameProfiler profiler = new FrameProfiler(4, () -> clock.now);
        int work = profiler.section("Work", FrameProfiler.ROOT);
        profiler.beginFrame();
        profiler.begin(work);
        profiler.end(work);
        profiler.endFrame();
        assertEquals(0, profiler.getFrameCount(), "not recording");

        profiler.setRecording(true);
        profiler.beginFrame();
        clock.advance(7);
        profiler.endFrame();
        profiler.setPaused(true);
        profiler.beginFrame();
        assertFalse(profiler.isInFrame());
        profiler.endFrame();

        assertEquals(1, profiler.getFrameCount(), "the paused frame was not kept");
        assertEquals(7 * MS, profiler.getFrameNanos(0));
        profiler.setPaused(false);
        profiler.beginFrame();
        profiler.endFrame();
        assertEquals(0L, profiler.getPeriodNanos(1), "no period across a pause");
    }

    @Test
    void shouldRecordEachSectionsCounterGrowth() {
        FrameProfiler profiler = recording(2);
        Draws draws = new Draws();
        profiler.setCounterSource(draws);
        int scene = profiler.section("Scene view", FrameProfiler.ROOT);
        int models = profiler.section("3D scene", scene);
        int ui = profiler.section("Editor UI", FrameProfiler.ROOT);

        profiler.beginFrame();
        profiler.begin(scene);
        draws.draws += 2; // the grid
        profiler.begin(models);
        draws.draws += 5;
        draws.vertices += 900;
        profiler.end(models);
        profiler.end(scene);
        profiler.begin(ui);
        draws.draws += 40;
        profiler.end(ui);
        profiler.endFrame();

        assertEquals(1, draws.framesStarted);
        assertEquals(7, profiler.getSectionCount(0, scene, 0), "a parent includes its children");
        assertEquals(5, profiler.getSectionCount(0, models, 0));
        assertEquals(900, profiler.getSectionCount(0, models, 1));
        assertEquals(40, profiler.getSectionCount(0, ui, 0));
        assertEquals("Vertices", profiler.getCounterName(1));
    }

    @Test
    void shouldKeepFrameValues() {
        FrameProfiler profiler = recording(2);
        int entities = profiler.value("Entities");
        assertEquals(entities, profiler.value("Entities"));

        profiler.beginFrame();
        profiler.set(entities, 12);
        profiler.add(entities, 3);
        profiler.endFrame();

        assertEquals(15, profiler.getValue(0, entities));
        assertEquals("Entities", profiler.getValueName(entities));
    }

    @Test
    void shouldDropAnOpenSectionAndAFrameLeftUnfinished() {
        FrameProfiler profiler = recording(4);
        int script = profiler.section("Script", FrameProfiler.ROOT);
        profiler.beginFrame();
        profiler.begin(script); // a script threw; its section was never closed
        clock.advance(5);
        profiler.endFrame();
        assertEquals(0L, profiler.getSectionNanos(0, script));

        profiler.beginFrame();
        profiler.begin(script);
        clock.advance(1);
        profiler.beginFrame(); // the previous frame never ended: it is replaced, not kept half done
        profiler.end(script);
        profiler.endFrame();
        assertEquals(2, profiler.getFrameCount());
        assertEquals(0L, profiler.getSectionNanos(0, script));
    }

    @Test
    void shouldRejectInvalidArgumentsAndTooManySections() {
        assertThrows(IllegalArgumentException.class, () -> new FrameProfiler(0, () -> 0L));
        FrameProfiler profiler = recording(1);
        profiler.beginFrame();
        assertThrows(IllegalArgumentException.class, () -> profiler.begin(0));
        assertThrows(IllegalStateException.class, () -> profiler.setCounterSource(new Draws()));
        profiler.endFrame();
        for (int i = 0; i < FrameProfiler.MAX_SECTIONS; i++) {
            profiler.section("Section " + i, FrameProfiler.ROOT);
        }
        IllegalStateException full = assertThrows(IllegalStateException.class, () -> profiler.section("One more", FrameProfiler.ROOT));
        assertTrue(full.getMessage().contains("One more"), full.getMessage());
    }
}
