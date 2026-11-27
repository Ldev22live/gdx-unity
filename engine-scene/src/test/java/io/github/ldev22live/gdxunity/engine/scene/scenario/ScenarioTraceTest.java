package io.github.ldev22live.gdxunity.engine.scene.scenario;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.BoxCollider3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.physics3d.serialization.CharacterController3DComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.PlayerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.play.serialization.SpawnPointComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The desktop half of the shared rule trace (roadmap A14, TD-109): {@code fixtures/contract-v1/scenario-trace/valley.json} scripts
 * one Play of the valley fixture's scenario; this engine's {@link ScenarioRuntime} must give exactly the recorded rule log and end
 * state. The web's {@code engine-babylon/test/scenarioRuntime.test.ts} replays the same file, so both engines run rules alike.
 */
class ScenarioTraceTest {
    private static final Path TRACE = Path.of("..", "fixtures", "contract-v1", "scenario-trace", "valley.json");

    @Test
    void shouldReplayTheSharedTraceExactlyAsTheWebDoes() throws IOException {
        JsonValue trace = new JsonReader().parse(Files.readString(TRACE));
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        registry.register(new BoxCollider3DComponentSerializer());
        registry.register(new CharacterController3DComponentSerializer());
        registry.register(new PlayerComponentSerializer());
        registry.register(new SpawnPointComponentSerializer());
        registry.register(new OwnerComponentSerializer());
        Scene scene = new SceneSerializer(registry).fromJson(Files.readString(TRACE.getParent().resolve(trace.getString("scene"))));
        List<String> log = new ArrayList<>();
        ScenarioRuntime runtime = ScenarioRuntime.create(scene, log::add);

        for (JsonValue step = trace.get("steps").child; step != null; step = step.next) {
            JsonValue enter = step.get("enter");
            if (enter != null) {
                runtime.onEnter(scene.findEntityById(UUID.fromString(enter.getString("area"))),
                    scene.findEntityById(UUID.fromString(enter.getString("visitor"))));
            } else {
                for (int i = 0; i < step.getInt("frames"); i++) {
                    runtime.update(step.getFloat("seconds"));
                }
            }
        }

        JsonValue expect = trace.get("expect");
        assertEquals(List.of(expect.get("log").asStringArray()), log);
        assertEquals(expect.getString("message"), runtime.getMessage());
        assertEquals(List.of(expect.get("objectiveLines").asStringArray()),
            new ScenarioRuntimeComponent(runtime).objectiveLines());
        assertEquals(expect.getString("result"), runtime.getResult() == null ? null : runtime.getResult().name());
        assertEquals(List.of(expect.get("skipped").asStringArray()), runtime.getSkipped());
    }
}
