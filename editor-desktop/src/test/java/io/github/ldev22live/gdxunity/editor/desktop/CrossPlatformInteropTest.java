package io.github.ldev22live.gdxunity.editor.desktop;

import com.badlogic.gdx.utils.GdxNativesLoader;
import io.github.ldev22live.gdxunity.editor.core.project.contract.AssetInventory;
import io.github.ldev22live.gdxunity.editor.core.project.contract.CapabilityPolicy;
import io.github.ldev22live.gdxunity.editor.core.project.contract.LocalProjectStore;
import io.github.ldev22live.gdxunity.editor.core.project.contract.ProjectManifest;
import io.github.ldev22live.gdxunity.editor.core.project.contract.ProjectStore;
import io.github.ldev22live.gdxunity.engine.desktop.GameComponentSerializers;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponent;
import io.github.ldev22live.gdxunity.engine.scene.scenario.ScenarioValidator;
import io.github.ldev22live.gdxunity.engine.scene.serialization.ComponentSerializerRegistry;
import io.github.ldev22live.gdxunity.engine.scene.serialization.PreservedComponent;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSemanticDiff;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Desktop half of the cross-platform round trips (TD-106, roadmap A12.10). The two clients cannot run in one process, so each step
 * writes a project folder under {@code fixtures/contract-v1/interop/} that the other client's test reads:
 * <ol>
 *   <li>{@code 1-desktop} (this test): the contract fixture opened and saved by the desktop;</li>
 *   <li>{@code 2-web} ({@code engine-babylon/test/interop.test.ts}): the web opens 1, edits supported fields, saves;</li>
 *   <li>{@code 3-desktop} (this test): the desktop opens 2, checks the web's edits and everything else, edits advanced fields, saves;</li>
 *   <li>the web test reopens 3 and checks both clients' edits;</li>
 *   <li>reverse: {@code r1-web} (web creates a project), {@code r2-desktop} (this test opens, edits and saves it), web reopens r2.</li>
 * </ol>
 * Ids and times are fixed, so each step writes the same bytes every run: a step's committed folder must equal what it produces now,
 * or the test fails. Set {@code GDXUNITY_UPDATE_INTEROP=1} to rewrite them (then run the web test with {@code UPDATE_INTEROP=1}).
 */
class CrossPlatformInteropTest {
    private static final Path FIXTURE = Path.of("..", "fixtures", "contract-v1", "valley-project");
    private static final Path INTEROP = Path.of("..", "fixtures", "contract-v1", "interop");
    private static final String SCENE = "scenes/valley.json";
    private static final boolean UPDATE = "1".equals(System.getenv("GDXUNITY_UPDATE_INTEROP"));

    @TempDir
    Path temp;

    private final SceneSerializer serializer = new SceneSerializer(registry());
    private final SceneSemanticDiff diff = new SceneSemanticDiff(SceneSemanticDiff.DESKTOP);

    @BeforeAll
    static void natives() {
        GdxNativesLoader.load();
    }

    private static ComponentSerializerRegistry registry() {
        ComponentSerializerRegistry registry = new ComponentSerializerRegistry();
        GameComponentSerializers.registerAll(registry);
        return registry;
    }

    /** A fixed sequence of name-based UUIDs, so a step writes the same ids every run. */
    private static Supplier<String> ids(String step) {
        int[] next = {0};
        return () -> UUID.nameUUIDFromBytes((step + "-" + next[0]++).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static LocalProjectStore store(Path root, String step, String time) {
        return new LocalProjectStore(root, CapabilityPolicy.desktop(), "1.0.0-interop",
            Clock.fixed(Instant.parse(time), ZoneOffset.UTC), Map.of(), ids(step));
    }

    @Test
    void step1DesktopSavesTheFixtureProject() throws IOException {
        Path project = copy(FIXTURE, temp.resolve("p1"));
        LocalProjectStore store = store(project, "1-desktop", "2026-11-25T14:00:00Z");
        ProjectStore.OpenResult opened = store.open();
        String original = Files.readString(project.resolve(SCENE));

        String saved = serializer.toJson(serializer.fromJson(original));
        assertEquals(List.of(), diff.compare(original, saved), "the desktop keeps everything");
        ProjectManifest manifest = assertInstanceOf(ProjectStore.SaveResult.Saved.class,
            store.save(opened.revision(), Map.of(SCENE, saved), Set.of("scene/1", "preserve-unknown/1", "scenario/1", "ownership/1"),
                false)).manifest();
        assertEquals(opened.manifest().projectId(), manifest.projectId());

        matchOrWrite(project, INTEROP.resolve("1-desktop"));
    }

    @Test
    void step3DesktopOpensTheWebSaveChecksItEditsAdvancedFieldsAndSaves() throws IOException {
        Path webSaved = INTEROP.resolve("2-web");
        assertTrue(Files.isDirectory(webSaved), "run the web test first: " + webSaved);
        Path project = copy(webSaved, temp.resolve("p3"));
        LocalProjectStore store = store(project, "3-desktop", "2026-11-25T14:20:00Z");
        ProjectStore.OpenResult opened = store.open();

        assertEquals(List.of(), opened.notes(), "hashes, marker and capabilities agree after a web save");
        assertEquals("web", opened.manifest().revision().client());
        assertEquals(manifestOf(INTEROP.resolve("1-desktop")).revision().id(), opened.manifest().revision().base(),
            "the web's revision is based on the desktop's");
        String webText = Files.readString(project.resolve(SCENE));
        Scene scene = serializer.fromJson(webText);
        Entity house = scene.findEntityByName("House");
        assertEquals(720f, house.getTransform().getLocalPosition().x, 0.001f, "the web's move arrived");
        assertTrue(scene.findEntityByName("Boulder") != null, "the web's rename arrived");
        assertEquals("ExampleFutureComponent", scene.findEntityByName("Hero").getComponent(PreservedComponent.class).getTypeId());
        assertEquals(List.of(), ScenarioValidator.validate(scene), "scenario and ownership survived the web");
        assertEquals(List.of(), diff.compare(webText, serializer.toJson(scene)), "the desktop keeps the web's file as it is");

        // Advanced desktop edits: the tower changes owner, the sun is brighter
        scene.findEntityByName("Tower").getComponent(OwnerComponent.class).setPlayerId(house.getComponent(OwnerComponent.class).getPlayerId());
        scene.findEntityByName("Sun").getComponent(DirectionalLightComponent.class).setIntensity(1.5f);
        String saved = serializer.toJson(scene);
        List<String> changed = diff.compare(webText, saved).stream().map(SceneSemanticDiff.Difference::path).toList();
        assertEquals(2, changed.size(), changed.toString());
        assertInstanceOf(ProjectStore.SaveResult.Saved.class, store.save(opened.revision(), Map.of(SCENE, saved), Set.of(), false));

        matchOrWrite(project, INTEROP.resolve("3-desktop"));
    }

    @Test
    void stepR2DesktopOpensAWebCreatedProjectEditsAndSaves() throws IOException {
        Path webCreated = INTEROP.resolve("r1-web");
        assertTrue(Files.isDirectory(webCreated), "run the web test first: " + webCreated);
        Path project = copy(webCreated, temp.resolve("r2"));
        LocalProjectStore store = store(project, "r2-desktop", "2026-11-25T14:40:00Z");
        ProjectStore.OpenResult opened = store.open();
        assertEquals(List.of(), opened.notes());
        assertEquals("web", opened.manifest().revision().client());
        assertEquals(null, opened.manifest().revision().base(), "the web created it");
        String scenePath = opened.manifest().startScene();
        String webText = Files.readString(project.resolve(scenePath));

        Scene scene = serializer.fromJson(webText);
        assertEquals(List.of(), diff.compare(webText, serializer.toJson(scene)), "the desktop keeps a web-made scene as it is");
        Entity crate = scene.findEntityByName("Crate");
        assertTrue(crate.getComponent(OwnerComponent.class) != null, "web-made ownership is read");
        crate.getTransform().setLocalScale(2f, 2f, 2f);
        String saved = serializer.toJson(scene);
        List<SceneSemanticDiff.Difference> differences = diff.compare(webText, saved);
        assertTrue(!differences.isEmpty() && differences.stream().allMatch(d -> d.path().contains("Crate") && d.path().contains("scale")),
            "only the desktop's edit differs: " + differences);
        assertInstanceOf(ProjectStore.SaveResult.Saved.class, store.save(opened.revision(), Map.of(scenePath, saved), Set.of(), false));
        assertEquals(List.of(), AssetInventory.verify(project, store.open().manifest()));

        matchOrWrite(project, INTEROP.resolve("r2-desktop"));
    }

    private static ProjectManifest manifestOf(Path project) throws IOException {
        return ProjectManifest.parse(Files.readString(project.resolve(ProjectManifest.FILE_NAME)));
    }

    /** The committed step folder must hold exactly what this step wrote; with the update flag it is replaced. */
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
            for (Path p : walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.delete(p);
            }
        }
    }
}
