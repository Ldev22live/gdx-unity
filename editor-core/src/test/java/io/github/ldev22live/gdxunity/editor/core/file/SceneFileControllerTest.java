package io.github.ldev22live.gdxunity.editor.core.file;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService.UnsavedChangesChoice;
import io.github.ldev22live.gdxunity.engine.core.asset.AssetType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SceneFileControllerTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private EditorState editorState;
    private FakeDialogs dialogs;
    private SceneFileController files;

    @BeforeEach
    void setUp() {
        editorState = new EditorState();
        dialogs = new FakeDialogs();
        files = new SceneFileController(editorState, dialogs, tempDir.toFile());
    }

    @Test
    void shouldStartCleanAndBecomeDirtyOnEditorCommands() {
        assertFalse(files.isDirty());
        assertEquals("Untitled - Forgeroom Editor", files.getWindowTitle());

        editorState.createEntity("Thing");
        assertTrue(files.isDirty());
        assertEquals("Untitled* - Forgeroom Editor", files.getWindowTitle());
    }

    @Test
    void shouldRunBeforeSaveHooksBeforeWriting() throws Exception {
        io.github.ldev22live.gdxunity.engine.scene.Entity thing = editorState.createEntity("Thing");
        thing.getTransform().setLocalPosition(500f, 0f, 0f);
        files.addBeforeSaveHook(() -> thing.getTransform().setLocalPosition(0f, 0f, 0f));
        dialogs.nextSaveFile = tempDir.resolve("hooked.json").toFile();

        assertTrue(files.save());

        String saved = java.nio.file.Files.readString(tempDir.resolve("hooked.json"));
        assertFalse(saved.contains("500"), saved);
    }

    @Test
    void shouldAskForFileOnFirstSaveAndAppendExtension() {
        editorState.createEntity("Thing");
        dialogs.nextSaveFile = tempDir.resolve("level1").toFile();

        assertTrue(files.save());
        File saved = tempDir.resolve("level1.json").toFile();
        assertTrue(saved.isFile());
        assertEquals(saved, files.getCurrentFile());
        assertFalse(files.isDirty());
        assertEquals("level1.json - Forgeroom Editor", files.getWindowTitle());

        // Second save goes straight to the same file
        editorState.createEntity("Another");
        assertTrue(files.save());
        assertEquals(1, dialogs.saveDialogs);
    }

    @Test
    void shouldOpenASampleAsAnUntitledCopyAndNeverWriteBackToTheOriginal() throws Exception {
        File samples = Files.createDirectory(tempDir.resolve("samples")).toFile();
        editorState.createEntity("Shipped");
        dialogs.nextSaveFile = new File(samples, "game.json");
        files.save();
        byte[] original = Files.readAllBytes(new File(samples, "game.json").toPath());
        files.newScene();
        files.setReadOnlyFolder(samples);

        dialogs.nextOpenFile = new File(samples, "game.json");
        assertTrue(files.open());

        assertNotNull(editorState.getActiveScene().findEntityByName("Shipped"));
        assertNull(files.getCurrentFile(), "a sample opens as a copy without a file");
        assertEquals("Untitled - Forgeroom Editor", files.getWindowTitle());
        editorState.createEntity("Mine");
        dialogs.nextSaveFile = tempDir.resolve("mine.json").toFile();
        assertTrue(files.save(), "Save asks for a place for the copy");
        assertEquals(tempDir.resolve("mine.json").toFile(), files.getCurrentFile());
        assertArrayEquals(original, Files.readAllBytes(new File(samples, "game.json").toPath()), "the original is untouched");
    }

    @Test
    void shouldRefuseToSaveIntoTheReadOnlyFolderEvenWhenAskedTo() throws Exception {
        File samples = Files.createDirectory(tempDir.resolve("samples")).toFile();
        files.setReadOnlyFolder(samples);
        editorState.createEntity("Thing");
        dialogs.nextSaveFile = new File(samples, "overwrite.json");

        assertFalse(files.saveAs());

        assertFalse(new File(samples, "overwrite.json").exists());
        assertTrue(files.isDirty(), "nothing was saved");
        assertTrue(files.isReadOnly(new File(samples, "deep/er/file.json")));
        assertFalse(files.isReadOnly(tempDir.resolve("elsewhere.json").toFile()));
    }

    @Test
    void shouldNotSaveWhenSaveDialogIsCancelled() {
        editorState.createEntity("Thing");
        dialogs.nextSaveFile = null;
        assertFalse(files.saveAs());
        assertTrue(files.isDirty());
        assertNull(files.getCurrentFile());
    }

    @Test
    void shouldRoundTripSceneThroughOpen() throws Exception {
        editorState.createEntity("Persisted");
        dialogs.nextSaveFile = tempDir.resolve("round.json").toFile();
        assertTrue(files.save());

        assertTrue(files.newScene());
        assertNull(editorState.getActiveScene().findEntityByName("Persisted"));

        dialogs.nextOpenFile = tempDir.resolve("round.json").toFile();
        assertTrue(files.open());
        assertNotNull(editorState.getActiveScene().findEntityByName("Persisted"));
        assertFalse(files.isDirty());
    }

    @Test
    void shouldKeepSceneWhenUserCancelsUnsavedChangesPrompt() {
        editorState.createEntity("Unsaved");
        dialogs.unsavedAnswer = UnsavedChangesChoice.CANCEL;

        assertFalse(files.newScene());
        assertNotNull(editorState.getActiveScene().findEntityByName("Unsaved"));
        assertTrue(files.isDirty());
        assertEquals(1, dialogs.unsavedPrompts);
    }

    @Test
    void shouldDiscardOrSaveBeforeContinuingWhenAsked() {
        editorState.createEntity("Unsaved");
        dialogs.unsavedAnswer = UnsavedChangesChoice.DISCARD;
        assertTrue(files.newScene());
        assertNull(editorState.getActiveScene().findEntityByName("Unsaved"));

        editorState.createEntity("KeepMe");
        dialogs.unsavedAnswer = UnsavedChangesChoice.SAVE;
        dialogs.nextSaveFile = tempDir.resolve("kept.json").toFile();
        assertTrue(files.confirmDiscardChanges());
        assertTrue(tempDir.resolve("kept.json").toFile().isFile());
    }

    @Test
    void shouldReportOpenFailureWithoutChangingDocument() throws Exception {
        Path broken = tempDir.resolve("broken.json");
        Files.writeString(broken, "{ not a scene");
        assertFalse(files.openFile(broken.toFile()));

        assertEquals(1, dialogs.errors.size());
        assertTrue(editorState.isStatusError());
        assertNull(files.getCurrentFile());
    }

    @Test
    void shouldSayAnAssetFileIsNotASceneInsteadOfShowingASchemaError() throws Exception {
        // A valid song, as the Music Studio writes it: it has no scene schemaVersion
        Path song = tempDir.resolve("demo.song");
        Files.writeString(song, "{ \"version\": 1, \"name\": \"Demo Beat\" }");

        assertFalse(files.openFile(song.toFile()));

        assertEquals(1, dialogs.errors.size());
        String message = dialogs.errors.get(0);
        assertTrue(message.contains("demo.song"), message);
        assertTrue(message.contains("not a scene"), message);
        assertTrue(message.contains("Content Browser"), message);
        assertFalse(message.contains("schemaVersion"), message);
        assertNull(files.getCurrentFile());
    }

    @Test
    void shouldOpenASongGraphClipAndSpriteInTheirOwnEditorWithoutTouchingTheScene() throws Exception {
        editorState.createEntity("Unsaved");
        dialogs.unsavedAnswer = UnsavedChangesChoice.CANCEL;
        List<String> opened = new ArrayList<>();
        files.setAssetOpener((file, type) -> {
            opened.add(type + ":" + file.getName());
            return null;
        });
        String[] names = {"theme.song", "logic.graph", "walk.anim", "hero.sprite"};
        for (String name : names) {
            dialogs.nextOpenFile = tempDir.resolve(name).toFile();
            assertTrue(files.open(), name);
        }

        assertEquals(List.of("SONG:theme.song", "GRAPH:logic.graph", "ANIMATION:walk.anim", "SPRITE:hero.sprite"), opened);
        assertEquals(0, dialogs.unsavedPrompts, "opening an asset must not ask about the scene, which it leaves alone");
        assertNotNull(editorState.getActiveScene().findEntityByName("Unsaved"));
        assertTrue(files.isDirty());
        assertNull(files.getCurrentFile());
        assertTrue(dialogs.errors.isEmpty(), dialogs.errors.toString());
    }

    @Test
    void shouldReportWhyAnAssetCouldNotBeOpenedAndLeaveTheSceneAlone() {
        files.setAssetOpener((file, type) -> file.getName() + " is outside this project");
        dialogs.nextOpenFile = tempDir.resolve("far.song").toFile();

        assertFalse(files.open());

        assertEquals(List.of("far.song is outside this project"), dialogs.errors);
        assertTrue(editorState.isStatusError());
    }

    @Test
    void shouldRefuseEveryAssetWithNoEditorOfItsOwnSayingWhatItIsAndWhatFileOpenDoes() {
        files.setAssetOpener((file, type) -> {
            throw new AssertionError("no editor opens " + type);
        });
        // A prefab has no editor of its own: the Content Browser has nothing to double-click it into either
        String[] names = {"crate.prefab", "rock.glb", "wall.png", "boom.wav", "shape.sculpt", "rock.material", "valley.terrain"};
        for (String name : names) {
            dialogs.errors.clear();
            assertFalse(files.openFile(tempDir.resolve(name).toFile()), name);

            assertEquals(1, dialogs.errors.size(), name);
            String message = dialogs.errors.get(0);
            String kind = AssetType.fromPath(name).name().toLowerCase();
            assertTrue(message.contains(name) && message.contains("is a " + kind + " file, not a scene"), message);
            assertTrue(message.contains("opens scenes, songs, graphs, animation clips and sprites"), message);
            assertFalse(message.toLowerCase().contains("double-click"), "it must not promise a double-click that does nothing: " + message);
            assertFalse(message.contains("schemaVersion"), message);
            assertTrue(message.contains("File > Import Asset"), "it says how to bring a file in: " + message);
            assertTrue(message.contains(io.github.ldev22live.gdxunity.editor.core.asset.AssetOpening.guidance(AssetType.fromPath(name))),
                "and how this kind of file is used: " + message);
        }
        assertNull(files.getCurrentFile());
    }

    @Test
    void shouldOfferScenesAndTheOpenableAssetsInTheOpenDialogButOnlyScenesInTheSampleDialog() {
        dialogs.nextOpenFile = null;

        files.open();
        assertEquals(List.of("json", "song", "graph", "anim", "sprite"), dialogs.lastOpenExtensions);
        assertEquals("Open", dialogs.lastOpenTitle);

        files.openFrom("Open Sample", tempDir.toFile());
        assertEquals(List.of("json"), dialogs.lastOpenExtensions, "a sample is a scene");
    }

    @Test
    void shouldStillAskAboutUnsavedChangesAfterASceneIsChosenAndKeepTheSceneWhenCancelled() throws Exception {
        editorState.createEntity("Unsaved");
        Path other = tempDir.resolve("other.json");
        Files.writeString(other, editorState.getSerializer().toJson(new io.github.ldev22live.gdxunity.engine.scene.Scene("Other")));
        dialogs.nextOpenFile = other.toFile();
        dialogs.unsavedAnswer = UnsavedChangesChoice.CANCEL;

        assertFalse(files.open());

        assertEquals(1, dialogs.unsavedPrompts, "a scene replaces the open one");
        assertNotNull(editorState.getActiveScene().findEntityByName("Unsaved"));
    }

    @Test
    void shouldNotifyListenersWhenTitleChanges() {
        List<String> titles = new ArrayList<>();
        files.addListener(controller -> titles.add(controller.getWindowTitle()));
        editorState.createEntity("Thing");
        assertEquals(List.of("Untitled* - Forgeroom Editor"), titles);
    }

    @Test
    void shouldOpenSamplesFromTheGivenFolder() throws Exception {
        editorState.createEntity("Sample Entity");
        dialogs.nextSaveFile = tempDir.resolve("sample.json").toFile();
        assertTrue(files.save());
        assertTrue(files.newScene());
        File samples = tempDir.resolve("samples").toFile();

        dialogs.nextOpenFile = tempDir.resolve("sample.json").toFile();
        assertTrue(files.openFrom("Open Sample", samples));

        assertEquals(samples, dialogs.lastOpenDirectory);
        assertEquals("Open Sample", dialogs.lastOpenTitle);
        assertNotNull(editorState.getActiveScene().findEntityByName("Sample Entity"));
    }

    @Test
    void shouldMakeATemplateProjectFromTheChosenSampleWithoutAskingAboutOrTouchingTheOpenScene() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source.resolve("samples"));
        Files.writeString(source.resolve("samples/game.json"), "{\"schemaVersion\":1,\"name\":\"Game\",\"entities\":[]}");
        File projects = tempDir.resolve("projects").toFile();
        editorState.createEntity("Unsaved");
        dialogs.nextOpenFile = source.resolve("samples/game.json").toFile();
        dialogs.unsavedAnswer = UnsavedChangesChoice.CANCEL;

        assertTrue(files.useAsTemplate(source.toFile(), source.resolve("samples").toFile(), projects));

        File project = new File(projects, "game");
        assertTrue(io.github.ldev22live.gdxunity.editor.core.project.EditorProject.isProject(project));
        assertTrue(new File(project, "scenes/game.json").isFile());
        assertEquals(0, dialogs.unsavedPrompts, "the open scene is not replaced, so nothing is asked about it");
        assertNotNull(editorState.getActiveScene().findEntityByName("Unsaved"));
        assertTrue(files.isDirty());
        assertFalse(editorState.isStatusError());
        String message = editorState.getStatusMessage();
        assertTrue(message.contains("--project") && message.contains(project.getAbsolutePath()) && message.contains("The sample is unchanged"), message);
        assertEquals(List.of("json"), dialogs.lastOpenExtensions, "a template starts from a scene");
        assertEquals("Use Sample as Template", dialogs.lastOpenTitle);
        assertTrue(Files.isRegularFile(source.resolve("samples/game.json")), "the sample is where it was");
    }

    @Test
    void shouldMakeNothingWhenTheTemplateDialogIsCancelledOrTheFileIsOutsideTheProjectOrBroken() throws Exception {
        Path source = tempDir.resolve("source");
        Files.createDirectories(source.resolve("samples"));
        Files.writeString(source.resolve("samples/broken.json"), "{ not a scene");
        Files.writeString(source.resolve("samples/bad.json"),
            "{\"entities\":[{\"components\":[{\"data\":{\"graph\":\"graphs/x.graph\"}}]}]}");
        Files.createDirectories(source.resolve("graphs"));
        Files.writeString(source.resolve("graphs/x.graph"), "{ not json");
        Path elsewhere = tempDir.resolve("elsewhere.json");
        Files.writeString(elsewhere, "{\"entities\":[]}");
        File projects = tempDir.resolve("projects").toFile();

        dialogs.nextOpenFile = null;
        assertFalse(files.useAsTemplate(source.toFile(), source.resolve("samples").toFile(), projects));
        assertTrue(dialogs.errors.isEmpty(), "cancelling is not an error");

        dialogs.nextOpenFile = elsewhere.toFile();
        assertFalse(files.useAsTemplate(source.toFile(), source.resolve("samples").toFile(), projects));
        assertTrue(dialogs.errors.get(0).contains("is not inside this project"), dialogs.errors.toString());

        dialogs.errors.clear();
        dialogs.nextOpenFile = source.resolve("samples/bad.json").toFile();
        assertFalse(files.useAsTemplate(source.toFile(), source.resolve("samples").toFile(), projects));
        assertTrue(dialogs.errors.get(0).contains("graphs/x.graph"), dialogs.errors.toString());
        assertTrue(editorState.isStatusError());
        String[] left = projects.list();
        assertTrue(left == null || left.length == 0, "a failed template leaves no folder");
    }

    private static class FakeDialogs implements EditorDialogService {
        File nextOpenFile;
        File nextSaveFile;
        UnsavedChangesChoice unsavedAnswer = UnsavedChangesChoice.CANCEL;
        int saveDialogs;
        int unsavedPrompts;
        final List<String> errors = new ArrayList<>();

        File lastOpenDirectory;
        String lastOpenTitle;
        List<String> lastOpenExtensions = List.of();

        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, List<String> extensions) {
            lastOpenExtensions = extensions;
            return chooseOpenFile(title, initialDirectory, filterDescription, extensions.get(0));
        }

        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            lastOpenTitle = title;
            lastOpenDirectory = initialDirectory;
            return Optional.ofNullable(nextOpenFile);
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            saveDialogs++;
            return Optional.ofNullable(nextSaveFile);
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            unsavedPrompts++;
            return unsavedAnswer;
        }

        @Override
        public void showError(String title, String message) {
            errors.add(message);
        }
    }
}
