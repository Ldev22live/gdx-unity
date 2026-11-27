package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.project.contract.AssetInventory;
import io.github.ldev22live.gdxunity.editor.core.project.contract.CapabilityPolicy;
import io.github.ldev22live.gdxunity.editor.core.project.contract.LocalProjectStore;
import io.github.ldev22live.gdxunity.editor.core.project.contract.ProjectManifest;
import io.github.ldev22live.gdxunity.editor.core.project.contract.ProjectStore;
import io.github.ldev22live.gdxunity.editor.core.scenario.RulesEditor;
import io.github.ldev22live.gdxunity.editor.core.scenario.ScenarioEditor;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshPartEditor;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshSaveSession;
import io.github.ldev22live.gdxunity.editor.sculpt.MeshWorkspace;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.render.components.ModelComponent;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import io.github.ldev22live.gdxunity.engine.terrain.Terrain;
import io.github.ldev22live.gdxunity.engine.terrain.TerrainIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roadmap A15, the desktop half of the cross-platform authoring journey on one real project with ground, a model and a scenario
 * ({@code fixtures/contract-v1/journey-project}, written by {@code tools/make_journey_fixture.py}):
 * <ol>
 *   <li>j1 (here): the desktop opens the legacy project and saves its first revision, which makes the manifest;</li>
 *   <li>j2 ({@code engine-babylon/test/journey.test.ts}): the web sculpts the ground, places an owned object, changes a rule;</li>
 *   <li>j3 (here): the desktop finds every web edit, changes the wall's geometry (Create Unique Mesh) and saves;</li>
 *   <li>j4 (web): the web reopens it with both clients' edits and plays the scenario to victory.</li>
 * </ol>
 * Each step writes a folder under {@code fixtures/contract-v1/interop/} that must equal the committed one
 * ({@code GDXUNITY_UPDATE_INTEROP=1} rewrites it).
 */
class JourneyInteropTest {
    private static final Path START = Path.of("..", "fixtures", "contract-v1", "journey-project");
    private static final Path INTEROP = Path.of("..", "fixtures", "contract-v1", "interop");
    private static final String SCENE = "scenes/journey.json";
    private static final String TERRAIN = "terrains/Level.terrain";
    private static final boolean UPDATE = "1".equals(System.getenv("GDXUNITY_UPDATE_INTEROP"));

    @TempDir
    Path temp;

    private final SceneSerializer serializer = new SceneSerializer(registry());

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    private static ComponentSerializerRegistry registry() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return registry;
    }

    private static Supplier<String> ids(String step) {
        int[] next = {0};
        return () -> UUID.nameUUIDFromBytes((step + "-" + next[0]++).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static LocalProjectStore store(Path root, String step, String time) {
        return new LocalProjectStore(root, CapabilityPolicy.desktop(), "1.0.0-interop",
            Clock.fixed(Instant.parse(time), ZoneOffset.UTC), Map.of(), ids(step));
    }

    @Test
    void stepJ1DesktopSavesTheJourneyProjectsFirstRevision() throws IOException {
        Path project = copy(START, temp.resolve("j1"));
        LocalProjectStore store = store(project, "j1-desktop", "2026-11-27T09:00:00Z");
        ProjectStore.OpenResult opened = store.open();
        Scene scene = serializer.fromJson(Files.readString(project.resolve(SCENE)));
        assertEquals(List.of(), ScenarioValidator.validate(scene), "the starting scenario is valid");

        ProjectManifest manifest = assertInstanceOf(ProjectStore.SaveResult.Saved.class, store.save(opened.revision(),
            Map.of(SCENE, serializer.toJson(scene)), Set.of("scene/1", "preserve-unknown/1", "scenario/1", "ownership/1"), true)).manifest();

        assertEquals(List.of("models/wall.glb", SCENE, TERRAIN), manifest.assets().stream().map(ProjectManifest.AssetEntry::path).toList(),
            "the manifest lists the scene and the ground and model it uses");
        assertEquals(List.of(), AssetInventory.verify(project, manifest));
        matchOrWrite(project, INTEROP.resolve("j1-desktop"));
    }

    @Test
    void stepJ3DesktopFindsTheWebsEditsChangesTheWallsGeometryAndSaves() throws IOException {
        Path webSaved = INTEROP.resolve("j2-web");
        assertTrue(Files.isDirectory(webSaved), "run the web test first: " + webSaved);
        Path project = copy(webSaved, temp.resolve("j3"));
        LocalProjectStore store = store(project, "j3-desktop", "2026-11-27T09:20:00Z");
        ProjectStore.OpenResult opened = store.open();
        assertEquals(List.of(), opened.notes(), "hashes, marker and capabilities agree after the web's save");
        assertEquals("web", opened.manifest().revision().client());

        // The web's three edits arrived: sculpted ground, a placed object owned by Player 2, a changed rule
        Terrain ground = TerrainIO.fromJson(Files.readString(project.resolve(TERRAIN)));
        float highest = 0f;
        for (float height : ground.copyHeights()) {
            highest = Math.max(highest, height);
        }
        assertTrue(highest > 10f, "the web raised the ground: highest point " + highest);
        Scene scene = serializer.fromJson(Files.readString(project.resolve(SCENE)));
        assertEquals(List.of(), ScenarioValidator.validate(scene), "the scenario is still valid after the web's edits");
        Entity crate = scene.findEntityByName("Crate");
        assertNotNull(crate, "the web's placed object");
        EditorState editorState = new EditorState(serializer);
        editorState.setActiveScene(scene);
        String player2 = new ScenarioEditor(editorState).current().orElseThrow()
            .players().get(1).id();
        assertEquals(player2, crate.getComponent(OwnerComponent.class).getPlayerId());
        assertEquals("Find the gate, then rest", new RulesEditor(editorState).rules().get(0).get("then").get(0).getString("text"));

        // The desktop's edit: the wall's geometry, saved as a unique model for this wall only
        Entity wall = scene.findEntityByName("House Wall");
        MeshSaveSession session = new MeshSaveSession(project, new MeshWorkspace(project, wall).open(wall));
        assertTrue(session.drafts().getFirst().apply(MeshPartEditor.Tool.Extrude, MeshPartEditor.Kind.Face, 0, 0.2f, 0f, 0f));
        String unique = session.save(MeshSaveSession.Mode.UNIQUE, "Wall carved", editorState, false);
        assertEquals(unique, wall.getComponent(ModelComponent.class).getModelPath());
        assertTrue(Files.exists(project.resolve("models/wall.glb")), "the original model is kept");

        ProjectManifest manifest = assertInstanceOf(ProjectStore.SaveResult.Saved.class,
            store.save(opened.revision(), Map.of(SCENE, serializer.toJson(scene)), Set.of(), false)).manifest();
        assertTrue(manifest.assets().stream().anyMatch(a -> a.path().equals(unique)), "the new model is in the manifest");
        assertEquals(List.of(), AssetInventory.verify(project, manifest));
        matchOrWrite(project, INTEROP.resolve("j3-desktop"));
    }

    // ---- the same folder helpers as CrossPlatformInteropTest ----------------------------------------------------------------

    private static void matchOrWrite(Path produced, Path committed) throws IOException {
        Map<String, byte[]> files = files(produced);
        if (UPDATE) {
            if (Files.exists(committed)) {
                deleteTree(committed);
            }
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Path target = committed.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            return;
        }
        assertTrue(Files.isDirectory(committed), committed + " is missing: run with GDXUNITY_UPDATE_INTEROP=1");
        Map<String, byte[]> expected = files(committed);
        assertEquals(expected.keySet(), files.keySet(), "the step's files changed: run with GDXUNITY_UPDATE_INTEROP=1");
        List<String> differing = new ArrayList<>();
        for (String name : files.keySet()) {
            if (name.endsWith(".png") || name.endsWith(".glb")) {
                assertArrayEquals(expected.get(name), files.get(name), name);
                continue;
            }
            if (!new String(files.get(name), StandardCharsets.UTF_8).equals(new String(expected.get(name), StandardCharsets.UTF_8)
                .replace("\r\n", "\n"))) {
                differing.add(name);
            }
        }
        assertEquals(List.of(), differing, "the step now writes different bytes: run with GDXUNITY_UPDATE_INTEROP=1 and review");
    }

    private static Map<String, byte[]> files(Path root) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                String name = root.relativize(file).toString().replace('\\', '/');
                if (!name.equals(LocalProjectStore.LOCK)) {
                    out.put(name, Files.readAllBytes(file));
                }
            }
        }
        return out;
    }

    private static Path copy(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
        return to;
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.delete(path);
            }
        }
    }
}
