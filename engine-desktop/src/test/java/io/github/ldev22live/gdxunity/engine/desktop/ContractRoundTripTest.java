package io.github.ldev22live.gdxunity.engine.desktop;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.PreservedComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSemanticDiff;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Desktop load and save keeps every scene semantically (TD-106, roadmap A12.1 and A12.2), with the editor's and the game's full
 * serializer list: the contract fixture (whose eight members the pre-A12 serializer dropped) and every shipped sample. Differences
 * are allowed only by {@link SceneSemanticDiff#DESKTOP}.
 */
class ContractRoundTripTest {
    private static final Path FIXTURE = Path.of("..", "fixtures", "contract-v1", "valley-project", "scenes", "valley.json");
    private static final Path SAMPLES = Path.of("..", "assets", "samples");

    private final SceneSerializer serializer = new SceneSerializer(registry());
    private final SceneSemanticDiff diff = new SceneSemanticDiff(SceneSemanticDiff.DESKTOP);

    private static ComponentSerializerRegistry registry() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return registry;
    }

    @Test
    void shouldKeepTheEightMembersThePreviousSerializerDropped() throws IOException {
        String original = Files.readString(FIXTURE);
        Scene scene = serializer.fromJson(original);
        String saved = serializer.toJson(scene);

        assertEquals(List.of(), diff.compare(original, saved));
        JsonValue file = new JsonReader().parse(saved);
        assertNotNull(file.get("scenario"), "1: the scenario block");
        assertNotNull(file.get("exampleFutureSetting"), "2: the unknown top-level key");
        assertEquals(0.25f, entity(file, "Ground").get("components").get(0).get("data").getFloat("futureTerrainBlend"),
            "3: the unknown field");
        for (String owned : List.of("House", "Tower", "Rock", "Hero")) {
            assertNotNull(component(entity(file, owned), "OwnerComponent"), "4 to 7: the owner of " + owned);
        }
        assertEquals(3, component(entity(file, "Hero"), "ExampleFutureComponent").get("data").getInt("level"),
            "8: the unrecognised component");
        assertEquals(List.of(), ScenarioValidator.validate(scene), "the fixture's references all resolve");
    }

    @Test
    void shouldKeepUnknownDataWhenKnownFieldsAreEdited() throws IOException {
        String original = Files.readString(FIXTURE);
        Scene scene = serializer.fromJson(original);
        Entity house = scene.findEntityByName("House");
        house.getTransform().setLocalPosition(700f, 120f, -650f);
        house.getComponent(OwnerComponent.class).setPlayerId(scene.findEntityByName("Tower").getComponent(OwnerComponent.class)
            .getPlayerId());
        scene.findEntityByName("Hero").setName("Explorer");

        List<SceneSemanticDiff.Difference> differences = diff.compare(original, serializer.toJson(scene));

        List<String> paths = differences.stream().map(SceneSemanticDiff.Difference::path).toList();
        assertEquals(4, differences.size(), differences.toString());
        assertTrue(paths.stream().allMatch(p -> p.contains("House") && (p.endsWith("position.x") || p.endsWith("position.z")
            || p.endsWith("OwnerComponent#0].data.player")) || p.endsWith("Hero].name")), paths.toString());
        assertTrue(differences.stream().noneMatch(d -> d.kind() == SceneSemanticDiff.Kind.MISSING), "only the edits differ");
        assertTrue(scene.findEntityByName("Explorer").getComponents(PreservedComponent.class).size() == 1);
    }

    @Test
    void shouldKeepEveryShippedSampleThroughADesktopLoadAndSave() throws IOException {
        List<String> failures = new ArrayList<>();
        int files = 0;
        try (Stream<Path> paths = Files.list(SAMPLES)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                files++;
                String original = Files.readString(file);
                List<SceneSemanticDiff.Difference> differences = diff.compare(original, serializer.toJson(serializer.fromJson(original)));
                if (!differences.isEmpty()) {
                    failures.add(file.getFileName() + ": " + differences);
                }
            }
        }
        assertTrue(files >= 56, "the shipped samples are found: " + files);
        assertEquals(List.of(), failures);
    }

    private static JsonValue entity(JsonValue file, String name) {
        for (JsonValue e = file.get("entities").child; e != null; e = e.next) {
            if (name.equals(e.getString("name"))) {
                return e;
            }
        }
        throw new AssertionError("no entity " + name);
    }

    private static JsonValue component(JsonValue entity, String type) {
        for (JsonValue c = entity.get("components").child; c != null; c = c.next) {
            if (type.equals(c.getString("type"))) {
                return c;
            }
        }
        return null;
    }

    @Test
    void shouldNotTreatTheFixtureAsTrivial() throws IOException {
        // Guards the guard: the fixture must still contain the foreign data the other tests rely on
        String original = Files.readString(FIXTURE);
        assertFalse(new JsonReader().parse(original).get("scenario").isNull());
        assertTrue(original.contains("ExampleFutureComponent") && original.contains("futureTerrainBlend")
            && original.contains("exampleFutureSetting"));
    }
}
