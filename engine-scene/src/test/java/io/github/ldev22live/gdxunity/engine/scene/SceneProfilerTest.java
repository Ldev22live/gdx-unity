package io.github.ldev22live.gdxunity.engine.scene;

import io.github.ldev22live.gdxunity.engine.core.profile.FrameProfiler;
import io.github.ldev22live.gdxunity.engine.scene.scripting.EntityScriptBindingComponent;
import io.github.ldev22live.gdxunity.engine.scene.scripting.ScriptLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Roadmap M9.6: a profiled scene times each component type's updates in its own section. */
class SceneProfilerTest {
    private static final long MS = 1_000_000L;

    private long now;

    /** A component whose update takes a fixed time on the test's clock. */
    private class Slow extends BaseComponent {
        private final long millis;

        Slow(long millis) {
            this.millis = millis;
        }

        @Override
        public void onUpdate(float deltaTime) {
            now += millis * MS;
        }
    }

    private class Spinner extends Slow {
        Spinner() {
            super(1);
        }
    }

    private class Mover extends Slow {
        Mover() {
            super(4);
        }
    }

    @Test
    void shouldTimeEachComponentTypeUnderTheParentSection() {
        FrameProfiler profiler = new FrameProfiler(4, () -> now);
        profiler.setRecording(true);
        int sceneUpdate = profiler.section("Scene update", FrameProfiler.ROOT);
        Scene scene = new Scene("Profiled");
        for (int i = 0; i < 3; i++) {
            scene.createEntity("Coin " + i).addComponent(new Spinner());
        }
        scene.createEntity("Player").addComponent(new Mover());
        scene.setProfiler(profiler, sceneUpdate);

        profiler.beginFrame();
        scene.update(0.016f);
        profiler.endFrame();

        int spinners = profiler.section("Spinner", sceneUpdate);
        int movers = profiler.section("Mover", sceneUpdate);
        assertEquals(3 * MS, profiler.getSectionNanos(0, spinners), "three spinners add up");
        assertEquals(3, profiler.getSectionCalls(0, spinners));
        assertEquals(4 * MS, profiler.getSectionNanos(0, movers));
    }

    @Test
    void shouldGiveEachScriptItsOwnSectionInsteadOfOneForAllBindings() {
        FrameProfiler profiler = new FrameProfiler(4, () -> now);
        profiler.setRecording(true);
        Scene scene = new Scene("Scripted");
        scene.createEntity("Player").addComponent(new EntityScriptBindingComponent("scripts/PlayerController.java",
            "game.PlayerController", ScriptLanguage.JAVA));
        scene.createEntity("Enemy").addComponent(new EntityScriptBindingComponent("scripts/Patrol.kt", "Patrol", ScriptLanguage.KOTLIN));
        scene.createEntity("Unbound").addComponent(new EntityScriptBindingComponent());
        scene.setProfiler(profiler, FrameProfiler.ROOT);

        profiler.beginFrame();
        scene.update(0.016f);
        profiler.endFrame();

        assertEquals(List.of("Script PlayerController", "Script Patrol", "Script (none)"),
            IntStream.range(0, profiler.getSectionCount()).mapToObj(profiler::getSectionName).toList());

        EntityScriptBindingComponent renamed = new EntityScriptBindingComponent("scripts/A.java", "A", ScriptLanguage.JAVA);
        assertEquals("Script A", renamed.getProfilerLabel());
        renamed.setScriptClassName("game.B");
        assertEquals("Script B", renamed.getProfilerLabel(), "the kept label follows a changed class");
    }

    @Test
    void shouldTimeNothingWithoutAProfilerOrOutsideARecordedFrame() {
        FrameProfiler profiler = new FrameProfiler(4, () -> now);
        Scene scene = new Scene("Profiled");
        scene.createEntity("Player").addComponent(new Mover());
        scene.setProfiler(profiler, FrameProfiler.ROOT);

        scene.update(0.016f); // not recording
        assertEquals(0, profiler.getSectionCount(), "no section registered for a frame that is not recorded");

        scene.setProfiler(null, FrameProfiler.ROOT);
        profiler.setRecording(true);
        profiler.beginFrame();
        scene.update(0.016f);
        profiler.endFrame();
        assertEquals(0, profiler.getSectionCount());
        assertEquals(8 * MS, now, "the component still updated both times");
    }

    @Test
    void shouldRejectAParentTheProfilerDoesNotHave() {
        Scene scene = new Scene("Profiled");
        assertThrows(IllegalArgumentException.class, () -> scene.setProfiler(new FrameProfiler(), 3));
    }
}
