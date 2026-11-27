package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.play.PlayerComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The desktop half of the shared profile trace (roadmap A14, TD-110): {@code fixtures/contract-v1/profile-trace/trace.json} scripts
 * key presses on an Action RPG + 3D and a Platformer + 2.5D scene; the game runtime ({@link GameSession}, real Jolt) plays them and
 * the player's position after each recorded phase is {@code expected.json}. With {@code GDXUNITY_UPDATE_TRACE=1} the file is
 * rewritten; otherwise this run must reproduce it (the desktop is deterministic). The web's {@code profileTrace.test.ts} replays
 * the same trace and must land within the trace's tolerance.
 */
class ProfileTraceTest {
    private static final Path DIR = Path.of("..", "fixtures", "contract-v1", "profile-trace");
    /** How close a desktop rerun must come to its own recorded positions, in scene units (only float noise). */
    private static final float SAME_RUN_TOLERANCE = 0.5f;
    private static final Map<String, Integer> KEYS = Map.of("W", Keys.W, "A", Keys.A, "S", Keys.S, "D", Keys.D, "SPACE", Keys.SPACE,
        "Q", Keys.Q, "E", Keys.E);

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldPlayTheSharedProfileTraceAndMatchTheRecordedPositions() throws IOException {
        JsonValue trace = new JsonReader().parse(Files.readString(DIR.resolve("trace.json")));
        float frame = trace.getFloat("frameSeconds");
        StringBuilder expected = new StringBuilder("{\n");
        boolean firstRun = true;
        for (JsonValue run = trace.get("runs").child; run != null; run = run.next) {
            String sceneFile = run.getString("scene");
            ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
            GameComponentSerializers.registerAll(registry);
            Scene scene = new SceneSerializer(registry).fromJson(Files.readString(DIR.resolve(sceneFile)));
            Set<Integer> held = new HashSet<>();
            GameSession session = new GameSession(AssetSource.directory(new File(DIR.toString())), scene, new ScriptRegistry(),
                GameSession.CONSOLE);
            try {
                session.setInput(held::contains);
                assertEquals(List.of(), session.start(), sceneFile);
                assertTrue(session.getProfileMessage().startsWith("Playing as"), session.getProfileMessage());
                Entity player = scene.getAllEntities().stream().filter(e -> e.getComponent(PlayerComponent.class) != null).findFirst()
                    .orElseThrow();
                expected.append(firstRun ? "" : ",\n").append("  \"").append(sceneFile).append("\": {");
                firstRun = false;
                boolean firstRecord = true;
                for (JsonValue phase = run.get("phases").child; phase != null; phase = phase.next) {
                    held.clear();
                    for (String key : phase.get("keys").asStringArray()) {
                        held.add(KEYS.get(key));
                    }
                    for (int i = 0; i < phase.getInt("frames"); i++) {
                        session.update(frame);
                    }
                    String record = phase.getString("record", null);
                    if (record != null) {
                        Vector3 at = player.getTransform().getWorldPosition();
                        expected.append(firstRecord ? "\n" : ",\n").append(String.format(Locale.ROOT,
                            "    \"%s\": [%.1f, %.1f, %.1f]", record, at.x, at.y, at.z));
                        firstRecord = false;
                    }
                }
                expected.append("\n  }");
            } finally {
                session.dispose();
            }
        }
        expected.append("\n}\n");
        Path file = DIR.resolve("expected.json");
        if ("1".equals(System.getenv("GDXUNITY_UPDATE_TRACE")) || !Files.exists(file)) {
            Files.writeString(file, expected.toString());
            return;
        }
        assertSamePositions(new JsonReader().parse(Files.readString(file)), new JsonReader().parse(expected.toString()));
    }

    private static void assertSamePositions(JsonValue recorded, JsonValue now) {
        for (JsonValue run = recorded.child; run != null; run = run.next) {
            for (JsonValue point = run.child; point != null; point = point.next) {
                float[] want = point.asFloatArray();
                float[] got = now.get(run.name).get(point.name).asFloatArray();
                for (int axis = 0; axis < 3; axis++) {
                    assertEquals(want[axis], got[axis], SAME_RUN_TOLERANCE, run.name + " " + point.name + " axis " + axis
                        + ": the desktop no longer reproduces expected.json; rerun with GDXUNITY_UPDATE_TRACE=1 and review");
                }
            }
        }
    }
}
