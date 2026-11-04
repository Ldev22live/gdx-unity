package io.github.ldev22live.gdxunity.editor.core.file;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService.UnsavedChangesChoice;
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

    private static class FakeDialogs implements EditorDialogService {
        File nextOpenFile;
        File nextSaveFile;
        UnsavedChangesChoice unsavedAnswer = UnsavedChangesChoice.CANCEL;
        int saveDialogs;
        int unsavedPrompts;
        final List<String> errors = new ArrayList<>();

        File lastOpenDirectory;
        String lastOpenTitle;

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
