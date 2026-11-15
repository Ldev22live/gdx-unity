package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Roadmap R0: Duplicate must keep every component of every entity in the shipped samples. Each entity of each
 * {@code assets/samples/*.json} scene is duplicated with the editor's full serializer list, and the saved state of
 * the copy's components must equal the original's. A component type added later is covered as soon as a sample uses
 * it (and has a serializer, which the scene file format already requires).
 */
class DuplicateSampleEntitiesTest {

    /** Tests run in the module folder; the project's assets are one level up. */
    private static final Path SAMPLES = Path.of("..", "assets", "samples");
    /**
     * Largest difference between two saved numbers that still counts as the same value: none. A light's direction used to move in its
     * last digit each time it was copied (it was normalized again), which needed a tolerance here; since milestone M0.2 a unit direction is
     * kept exactly, so a copy must match exactly.
     */
    private static final double NUMBER_TOLERANCE = 0.0;

    @BeforeAll
    static void initNatives() {
        GdxNativesLoader.load();
    }

    @Test
    void shouldCopyEveryComponentOfEveryEntityInEverySampleScene() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        SceneSerializer serializer = new SceneSerializer(registry);

        List<Path> scenes;
        try (Stream<Path> files = Files.list(SAMPLES)) {
            scenes = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(scenes.isEmpty(), "no sample scenes found in " + SAMPLES.toAbsolutePath());

        List<String> problems = new ArrayList<>();
        int duplicated = 0;
        for (Path file : scenes) {
            Scene scene = serializer.fromJson(Files.readString(file));
            EditorState state = new EditorState(serializer);
            state.setActiveScene(scene);
            List<Entity> originals = new ArrayList<>(scene.getAllEntities());
            for (Entity original : originals) {
                state.setSelectedEntity(original);
                Entity copy = state.duplicateSelectedEntity();
                String where = file.getFileName() + " / " + original.getName();
                if (copy == null) {
                    problems.add(where + ": Duplicate returned nothing");
                    continue;
                }
                duplicated++;
                JsonValue expected = componentsOf(serializer, original);
                JsonValue actual = componentsOf(serializer, copy);
                if (!sameState(expected, actual)) {
                    problems.add(where + ": components differ\n  original: " + expected.toJson(JsonWriter.OutputType.json)
                        + "\n  copy:     " + (actual == null ? "none" : actual.toJson(JsonWriter.OutputType.json)));
                }
            }
        }

        assertEquals(List.of(), problems, "problems in " + duplicated + " duplicated entities");
    }

    @Test
    void shouldKeepEveryEntityAndComponentWhenEverySampleSceneIsSavedAndOpenedAgain() throws IOException {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        EditorApplication.registerComponentSerializers(registry);
        SceneSerializer serializer = new SceneSerializer(registry);

        List<Path> scenes;
        try (Stream<Path> files = Files.list(SAMPLES)) {
            scenes = files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(scenes.isEmpty(), "no sample scenes found in " + SAMPLES.toAbsolutePath());

        List<String> problems = new ArrayList<>();
        for (Path file : scenes) {
            Scene first = serializer.fromJson(Files.readString(file));
            Scene second = serializer.fromJson(serializer.toJson(first));
            if (first.getEntityCount() != second.getEntityCount()) {
                problems.add(file.getFileName() + ": " + first.getEntityCount() + " entities became " + second.getEntityCount());
                continue;
            }
            for (Entity before : first.getAllEntities()) {
                Entity after = second.findEntityById(before.getId());
                if (after == null) {
                    problems.add(file.getFileName() + " / " + before.getName() + ": missing after reopening");
                } else if (!sameState(componentsOf(serializer, before), componentsOf(serializer, after))) {
                    problems.add(file.getFileName() + " / " + before.getName() + ": components changed");
                }
            }
        }

        assertEquals(List.of(), problems);
    }

    private static JsonValue componentsOf(SceneSerializer serializer, Entity entity) {
        return serializer.entityToJsonValue(entity).get("components");
    }

    /**
     * Same structure and values; numbers may differ by {@link #NUMBER_TOLERANCE}, which is zero.
     */
    private static boolean sameState(JsonValue expected, JsonValue actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isNumber() && actual.isNumber()) {
            return Math.abs(expected.asDouble() - actual.asDouble()) <= NUMBER_TOLERANCE;
        }
        if (expected.type() != actual.type() || expected.size != actual.size) {
            return false;
        }
        if (expected.isObject() || expected.isArray()) {
            JsonValue a = expected.child;
            JsonValue b = actual.child;
            while (a != null && b != null) {
                if (!Objects.equals(a.name, b.name) || !sameState(a, b)) {
                    return false;
                }
                a = a.next;
                b = b.next;
            }
            return a == null && b == null;
        }
        return Objects.equals(expected.asString(), actual.asString());
    }
}
