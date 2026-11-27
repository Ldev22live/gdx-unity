package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetSource;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioRuntime;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game runtime runs scenario rules as editor Play does (TD-109): the shared contract fixture's "Welcome" timer message and its
 * "Arrived" rule, which completes the objective and ends in victory when the Hero (a Character Controller 3D, real Jolt) walks into
 * the house's door area.
 */
class GameSessionScenarioTest {
    private static final Path FIXTURE = Path.of("..", "fixtures", "contract-v1", "valley-project");

    private GameSession session;

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.dispose();
        }
    }

    @Test
    void shouldShowTheTimedMessageAndWinWhenTheHeroEntersTheDoorArea() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        Scene scene = new SceneSerializer(registry).fromJson(Files.readString(FIXTURE.resolve("scenes/valley.json")));
        List<String> info = new ArrayList<>();
        session = new GameSession(AssetSource.directory(new File(FIXTURE.toString())), scene, new ScriptRegistry(),
            new GameSession.Log() {
                @Override
                public void info(String message) {
                    info.add(message);
                }

                @Override
                public void error(String message) {
                    info.add("ERROR " + message);
                }
            });

        session.setInput(key -> false); // no keys pressed: the Hero only moves where the test puts it
        assertEquals(List.of(), session.start());
        ScenarioRuntime rules = session.getScenario();
        assertNotNull(rules, "the fixture has a scenario block");
        assertEquals(List.of(), rules.getSkipped(), "every fixture rule is valid");

        for (int i = 0; i < 25; i++) {
            session.update(0.1f);
        }
        assertEquals("Find the house in the valley.", rules.getMessage());
        assertFalse(rules.getObjectives().get(0).complete());
        assertNull(rules.getResult());

        Entity hero = scene.findEntityByName("Hero");
        Entity door = scene.findEntityByName("House Door Area");
        session.getPhysicsSystem3D().teleportCharacter(hero, door.getTransform().getWorldPosition());
        for (int i = 0; i < 10 && rules.getResult() == null; i++) {
            session.update(1f / 60f);
        }

        assertTrue(rules.getObjectives().get(0).complete(), info.toString());
        assertEquals(ScenarioRuntime.Result.VICTORY, rules.getResult());
        assertTrue(info.stream().anyMatch(line -> line.startsWith("Rules: Rule 'Arrived'")), info.toString());
    }
}
