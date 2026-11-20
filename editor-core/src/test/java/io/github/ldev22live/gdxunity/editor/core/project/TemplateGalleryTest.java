package io.github.ldev22live.gdxunity.editor.core.project;

import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.engine.render.components.CameraComponent;
import io.github.ldev22live.gdxunity.engine.render.components.MeshPrimitiveComponent;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.CameraComponentSerializer;
import io.github.ldev22live.gdxunity.engine.render.components.serialization.MeshPrimitiveComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.Component;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.GameMode;
import io.github.ldev22live.gdxunity.engine.scene.Scene;
import io.github.ldev22live.gdxunity.engine.scene.lighting.DirectionalLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.SkyLightComponent;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.DirectionalLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.lighting.serialization.SkyLightComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.SceneSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The template gallery's catalog and its empty starts (roadmap M1.4). Opening, playing and exporting each is in editor-desktop. */
class TemplateGalleryTest {
    /** Tests run in the module folder; the project's assets are one level up. */
    private static final File PROJECT = new File("../assets");

    @TempDir
    Path temp;

    @BeforeAll
    static void natives() {
        EditorTestHelper.initNatives();
    }

    private static SceneSerializer serializer() {
        SceneSerializer serializer = new SceneSerializer();
        serializer.getRegistry().register(new CameraComponentSerializer());
        serializer.getRegistry().register(new MeshPrimitiveComponentSerializer());
        serializer.getRegistry().register(new DirectionalLightComponentSerializer());
        serializer.getRegistry().register(new SkyLightComponentSerializer());
        return serializer;
    }

    @Test
    void shouldOfferTheThreeEmptyStartsFirstThenEverySampleSceneOnce() throws IOException {
        List<TemplateGallery.Choice> choices = TemplateGallery.choices(PROJECT);
        assertEquals(List.of(GameMode.MODE_2D, GameMode.MODE_2_5D, GameMode.MODE_3D),
            choices.subList(0, 3).stream().map(TemplateGallery.Choice::mode).toList());
        assertTrue(choices.subList(0, 3).stream().allMatch(c -> c.kind() == TemplateGallery.Kind.EMPTY && !c.canPlaySample()));
        Set<String> listed = new TreeSet<>();
        for (TemplateGallery.Choice sample : choices.subList(3, choices.size())) {
            assertEquals(TemplateGallery.Kind.SAMPLE, sample.kind());
            assertTrue(sample.canPlaySample());
            assertTrue(listed.add(sample.scene()), "listed once: " + sample.scene());
        }
        Set<String> scenes;
        try (Stream<Path> files = Files.list(PROJECT.toPath().resolve("samples"))) {
            scenes = files.map(p -> "samples/" + p.getFileName()).filter(name -> name.endsWith(".json"))
                .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(scenes, listed, "every sample scene is in " + TemplateGallery.CATALOG_FILE + " and nothing else is");
    }

    @Test
    void shouldTakeEachSamplesDimensionFromItsOwnSceneAndSizeFromWhatATemplateWouldCopy() throws IOException {
        TemplateGallery.Choice racing = sample("samples/racing-3d.json");
        assertEquals(GameMode.MODE_3D, racing.mode());
        assertEquals("Racing", racing.genre());
        TemplateProject.Plan plan = TemplateProject.plan(PROJECT, "samples/racing-3d.json");
        List<String> palette = StarterPalette.files(PROJECT).stream().filter(f -> !plan.files().contains(f)).toList();
        long paletteBytes = 0;
        for (String file : palette) {
            paletteBytes += Files.size(PROJECT.toPath().resolve(file));
        }
        // The palette's Kenney models bring their licence records (M2b.2), one more file of a known size
        List<String> copied = new ArrayList<>(plan.files());
        copied.addAll(palette);
        String licences = ProjectLicences.recordFor(PROJECT, copied).orElseThrow().toJson();
        assertEquals(plan.files().size() + palette.size() + 1 + 1, racing.files(),
            "the copied files, the starter palette, the licence records and the project file");
        assertEquals(plan.bytes() + paletteBytes + licences.getBytes(StandardCharsets.UTF_8).length, racing.bytes());
        assertTrue(racing.sizeText().matches("\\d+ files, \\d+ KB"), racing.sizeText());
        assertEquals(GameMode.MODE_2_5D, sample("samples/fighter-25d.json").mode());
        assertEquals(GameMode.MODE_2D, sample("samples/breakout-2d.json").mode());
    }

    @Test
    void shouldMakeAProjectOfExactlyTheFilesThePlanListed() throws IOException {
        TemplateProject.Plan plan = TemplateProject.plan(PROJECT, "samples/hopper-3d.json");
        TemplateProject.Result made = TemplateProject.create(PROJECT, "samples/hopper-3d.json", temp.toFile(), null);
        // The plan names the scene where the sample keeps it; the project keeps it in scenes/
        List<String> planned = plan.files().stream()
            .map(file -> file.equals("samples/hopper-3d.json") ? made.startScene() : file).sorted().toList();
        assertEquals(planned, made.files());
    }

    @Test
    void shouldSayWhichPlatformsATemplateIsKnownToWorkOn() throws IOException {
        assertEquals(List.of(TemplateGallery.DESKTOP_STATUS, TemplateGallery.WEB_PORTED_STATUS),
            sample("samples/collector-3d.json").platformText());
        assertEquals(List.of(TemplateGallery.DESKTOP_STATUS, TemplateGallery.WEB_UNKNOWN_STATUS),
            sample("samples/racing-3d.json").platformText());
    }

    @Test
    void shouldGiveAPreviewOnlyWhenTheImageExists() throws IOException {
        Path root = temp.resolve("project");
        Files.createDirectories(root.resolve("templates/previews"));
        Files.writeString(root.resolve("templates/previews/a.png"), "png");
        assertEquals("templates/previews/a.png", TemplateGallery.previewOf(root.toFile(), "samples/a.json"));
        assertNull(TemplateGallery.previewOf(root.toFile(), "samples/b.json"));
    }

    @Test
    void shouldRefuseACatalogThatNamesAMissingSceneOrASceneWithNoGameModeOrAnEntryWithNoTitle() throws IOException {
        Path root = temp.resolve("project");
        Files.createDirectories(root.resolve("samples"));
        Files.createDirectories(root.resolve("templates"));
        Files.writeString(root.resolve("samples/plain.json"), "{\"name\":\"Plain\",\"entities\":[]}");
        catalog(root, "{\"scene\":\"samples/missing.json\",\"title\":\"M\",\"genre\":\"G\",\"summary\":\"S\"}");
        IOException missing = assertThrows(IOException.class, () -> TemplateGallery.choices(root.toFile()));
        assertTrue(missing.getMessage().contains("samples/missing.json"), missing.getMessage());
        catalog(root, "{\"scene\":\"samples/plain.json\",\"title\":\"P\",\"genre\":\"G\",\"summary\":\"S\"}");
        IOException noMode = assertThrows(IOException.class, () -> TemplateGallery.choices(root.toFile()));
        assertTrue(noMode.getMessage().contains("no game mode"), noMode.getMessage());
        catalog(root, "{\"scene\":\"samples/plain.json\",\"genre\":\"G\",\"summary\":\"S\"}");
        IOException noTitle = assertThrows(IOException.class, () -> TemplateGallery.choices(root.toFile()));
        assertTrue(noTitle.getMessage().contains("\"title\""), noTitle.getMessage());
    }

    @Test
    void shouldOfferOnlyTheEmptyStartsWhenAProjectHasNoCatalog() throws IOException {
        assertEquals(TemplateGallery.emptyStarts(temp.toFile()), TemplateGallery.choices(temp.toFile()));
    }

    @Test
    void shouldFormatSizesInBytesKilobytesAndMegabytes() {
        assertEquals("512 bytes", TemplateGallery.formatBytes(512));
        assertEquals("2 KB", TemplateGallery.formatBytes(2048));
        assertEquals("1.5 MB", TemplateGallery.formatBytes(3 * 512 * 1024));
    }

    @Test
    void shouldMakeAnEmptyStartInEachModeWithACameraAndForDepthModesLightsAndAGround() throws IOException {
        SceneSerializer serializer = serializer();
        for (GameMode mode : GameMode.values()) {
            TemplateProject.Result made = StarterProject.create(temp.toFile(), null, mode, serializer);
            assertEquals(GameExporter.safeName(StarterProject.defaultName(mode)), made.folder().getName());
            assertTrue(EditorProject.isProject(made.folder()));
            Properties settings = new Properties();
            settings.load(Files.newBufferedReader(made.folder().toPath().resolve(EditorProject.MARKER_FILE), StandardCharsets.UTF_8));
            assertEquals(StarterProject.START_SCENE, settings.getProperty("startScene"));
            Scene scene = serializer.fromJson(Files.readString(made.folder().toPath().resolve(made.startScene())));
            assertEquals(mode, scene.getGameMode());
            Entity camera = scene.findEntityByName("Main Camera");
            assertNotNull(camera);
            assertEquals(mode == GameMode.MODE_2D ? CameraComponent.Projection.ORTHOGRAPHIC : CameraComponent.Projection.PERSPECTIVE,
                camera.getComponent(CameraComponent.class).getProjection());
            boolean depth = mode != GameMode.MODE_2D;
            assertEquals(depth, has(scene, "Sun", DirectionalLightComponent.class), mode + " sun");
            assertEquals(depth, has(scene, "Sky Light", SkyLightComponent.class), mode + " sky");
            assertEquals(depth, has(scene, "Ground", MeshPrimitiveComponent.class), mode + " ground");
            scene.dispose();
        }
    }

    @Test
    void shouldNeverOverwriteAnExistingProjectFolderWithAnEmptyStart() throws IOException {
        TemplateProject.Result first = StarterProject.create(temp.toFile(), "Game", GameMode.MODE_3D, serializer());
        Files.writeString(first.folder().toPath().resolve("mine.txt"), "keep");
        TemplateProject.Result second = StarterProject.create(temp.toFile(), "Game", GameMode.MODE_2D, serializer());
        assertEquals("Game", first.folder().getName());
        assertEquals("Game 2", second.folder().getName());
        assertEquals("keep", Files.readString(first.folder().toPath().resolve("mine.txt")));
        assertTrue(Files.readString(first.folder().toPath().resolve(StarterProject.START_SCENE)).contains("\"3d\""));
    }

    @Test
    void shouldLeaveNothingBehindWhenAnEmptyStartCannotBeWritten() {
        SceneSerializer broken = new SceneSerializer() {
            @Override
            public String toJson(Scene scene) {
                throw new IllegalStateException("cannot write");
            }
        };
        assertThrows(IllegalStateException.class, () -> StarterProject.create(temp.toFile(), "Broken", GameMode.MODE_3D, broken));
        assertFalse(Files.exists(temp.resolve("Broken")));
    }

    @Test
    void shouldMakeTheChoicesProjectThroughTheGalleryForBothKinds() throws IOException {
        TemplateProject.Result empty = TemplateGallery.create(TemplateGallery.emptyStarts(PROJECT).get(1), PROJECT, temp.toFile(), "Side",
            serializer());
        assertEquals("Side", empty.folder().getName());
        TemplateProject.Result sample = TemplateGallery.create(sample("samples/breakout-2d.json"), PROJECT, temp.toFile(), "",
            serializer());
        assertEquals("breakout-2d", sample.folder().getName());
        assertTrue(new File(sample.folder(), "scenes/breakout-2d.json").isFile());
    }

    /**
     * Roadmap M3c.5 (finding H2 of M0.4): a new 3D or 2.5D project gets project-owned copies of the World strip's objects and
     * characters, so Place has something to place; a 2D one gets none. The gallery's file count says so before the project is made.
     */
    @Test
    void shouldGiveNewDepthProjectsTheStarterPaletteAndCountItInTheGallery() throws IOException {
        List<String> palette = StarterPalette.files(PROJECT);
        assertTrue(palette.contains(StarterPalette.LEVEL_FOLDER + "/Oak Tree.prefab"), palette.toString());
        assertTrue(palette.contains(StarterPalette.CHARACTER_FOLDER + "/Player.prefab"), palette.toString());
        List<TemplateGallery.Choice> starts = TemplateGallery.emptyStarts(PROJECT);

        for (TemplateGallery.Choice start : starts) {
            TemplateProject.Result made = TemplateGallery.create(start, PROJECT, temp.toFile(), null, serializer());
            boolean depth = start.mode() != GameMode.MODE_2D;
            for (String file : palette) {
                assertEquals(depth, Files.isRegularFile(made.folder().toPath().resolve(file)), start.title() + ": " + file);
                assertEquals(depth, made.files().contains(file), start.title() + " lists " + file);
            }
            try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(made.folder().toPath())) {
                assertEquals(start.files(), walk.filter(Files::isRegularFile).count(), start.title() + ": the gallery's count is right");
            }
        }
        TemplateGallery.Choice valley = sample("samples/action-rpg-valley-3d.json");
        TemplateProject.Result made = TemplateGallery.create(valley, PROJECT, temp.toFile(), "Valley", serializer());
        assertTrue(Files.isRegularFile(made.folder().toPath().resolve(StarterPalette.CHARACTER_FOLDER + "/Spawn Point.prefab")),
            "a template gets the palette too");
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(made.folder().toPath())) {
            assertEquals(valley.files(), walk.filter(Files::isRegularFile).count(), "the gallery's count for a sample is right");
        }
    }

    private static boolean has(Scene scene, String name, Class<? extends Component> type) {
        Entity entity = scene.findEntityByName(name);
        return entity != null && entity.getComponent(type) != null;
    }

    private static TemplateGallery.Choice sample(String scene) throws IOException {
        return TemplateGallery.samples(PROJECT).stream().filter(c -> c.scene().equals(scene)).findFirst()
            .orElseThrow(() -> new AssertionError("not in the gallery: " + scene));
    }

    private static void catalog(Path root, String entry) throws IOException {
        Files.writeString(root.resolve(TemplateGallery.CATALOG_FILE), "{\"templates\":[" + entry + "]}");
    }
}
