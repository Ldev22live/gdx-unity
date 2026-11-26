package io.github.ldev22live.gdxunity.editor.core.file;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.project.contract.CapabilityPolicy;
import io.github.ldev22live.gdxunity.editor.core.project.contract.ProjectManifest;
import io.github.ldev22live.gdxunity.engine.scene.Entity;
import io.github.ldev22live.gdxunity.engine.scene.scenario.OwnerComponentSerializer;
import io.github.ldev22live.gdxunity.engine.scene.serialization.PreservedComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The editor's Open and Save under the shared project contract (TD-106, roadmap A12.8 and A12.11). */
class SceneFileControllerContractTest {
    private static final Path FIXTURE = Path.of("..", "fixtures", "contract-v1", "valley-project");

    @TempDir
    Path temp;

    private final List<String> errors = new ArrayList<>();

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private final EditorDialogService dialogs = new EditorDialogService() {
        @Override
        public Optional<File> chooseOpenFile(String title, File initialDirectory, String filterDescription, String extension) {
            return Optional.empty();
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File suggestedFile, String filterDescription, String extension) {
            return Optional.empty();
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            return UnsavedChangesChoice.CANCEL;
        }

        @Override
        public void showError(String title, String message) {
            errors.add(title + ": " + message);
        }

        @Override
        public boolean confirm(String title, String message) {
            questions.add(title);
            return agree;
        }
    };

    private final List<String> questions = new ArrayList<>();
    private boolean agree;

    /** The fixture as a legacy project: no manifest, a format-1 marker. */
    private Path legacy(String name) throws IOException {
        Path root = project(name);
        Files.delete(root.resolve(ProjectManifest.FILE_NAME));
        Files.writeString(root.resolve("gdxunity.project"), String.join("\n", "format=1", "name=Legacy",
            "startScene=scenes/valley.json", ""));
        return root;
    }

    @Test
    void shouldAskOnceBeforeAddingAManifestAndSaveAsBeforeWhenDeclined() throws IOException {
        Path root = legacy("declined");
        SceneFileController controller = opened(root);
        agree = false;
        editorOf(controller).getActiveScene().findEntityByName("Rock").setName("Stone");

        assertTrue(controller.save(), errors.toString());
        assertTrue(controller.save(), errors.toString());

        assertEquals(List.of("Add a project manifest?"), questions, "asked once per session");
        assertFalse(Files.exists(root.resolve(ProjectManifest.FILE_NAME)), "declined: no manifest");
        assertTrue(Files.readString(root.resolve("scenes/valley.json")).contains("Stone"), "saved as before");
        assertTrue(Files.readString(root.resolve("gdxunity.project")).startsWith("format=1"));
    }

    @Test
    void shouldAddTheManifestWhenThePersonAgreesAndThenSaveRevisions() throws IOException {
        Path root = legacy("agreed");
        SceneFileController controller = opened(root);
        agree = true;
        editorOf(controller).getActiveScene().findEntityByName("Rock").setName("Stone");

        assertTrue(controller.save(), errors.toString());

        ProjectManifest first = manifest(root);
        assertEquals(null, first.revision().base(), "the manifest's first revision");
        assertEquals(first.revision().id(), controller.getBaseRevision());
        assertTrue(Files.readString(root.resolve("gdxunity.project")).startsWith("format=2"));
        assertTrue(Files.readString(root.resolve("scenes/valley.json")).contains("Stone"));
        assertTrue(first.required().contains("scenario/1") && first.required().contains("ownership/1"),
            "the scene's scenario and owners are declared: " + first.required());

        editorOf(controller).getActiveScene().findEntityByName("Tower").setName("Keep");
        assertTrue(controller.save(), errors.toString());
        assertEquals(first.revision().id(), manifest(root).revision().base(), "later saves are revisions");
        assertEquals(1, questions.size());
    }

    private Path project(String name) throws IOException {
        Path target = temp.resolve(name);
        Files.walkFileTree(FIXTURE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(FIXTURE.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(FIXTURE.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
        return target;
    }

    private final java.util.Map<SceneFileController, EditorState> editors = new java.util.IdentityHashMap<>();

    private EditorState editorOf(SceneFileController controller) {
        return editors.get(controller);
    }

    private SceneFileController opened(Path root) {
        EditorState editor = new EditorState();
        editor.getSerializer().getRegistry().register(new OwnerComponentSerializer());
        SceneFileController controller = new SceneFileController(editor, dialogs, root.toFile());
        editors.put(controller, editor);
        assertTrue(controller.openFile(root.resolve("scenes/valley.json").toFile()), errors.toString());
        return controller;
    }

    private static ProjectManifest manifest(Path root) throws IOException {
        return ProjectManifest.parse(Files.readString(root.resolve(ProjectManifest.FILE_NAME)));
    }

    @Test
    void shouldSaveANewRevisionAndKeepEverythingItDoesNotEdit() throws IOException {
        Path root = project("edit");
        SceneFileController controller = opened(root);
        String opened = controller.getBaseRevision();
        assertEquals(CapabilityPolicy.Access.FULL, controller.getProjectAccess().access());

        Entity house = editorOf(controller).getActiveScene().findEntityByName("House");
        house.getTransform().setLocalPosition(650f, 120f, -600f);
        assertTrue(controller.save(), errors.toString());

        ProjectManifest saved = manifest(root);
        assertEquals(opened, saved.revision().base());
        assertEquals(saved.revision().id(), controller.getBaseRevision());
        String text = Files.readString(root.resolve("scenes/valley.json"));
        assertTrue(text.contains("\"scenario\"") && text.contains("ExampleFutureComponent") && text.contains("futureTerrainBlend")
            && text.contains("exampleFutureSetting") && text.contains("OwnerComponent"), "nothing unedited was lost");
        assertTrue(text.contains("650"), "the edit was saved");
    }

    @Test
    void shouldRefuseTheSecondEditorsSaveAsAConflictAndOfferACopy() throws IOException {
        Path root = project("conflict");
        SceneFileController first = opened(root);
        SceneFileController second = opened(root);
        assertEquals(first.getBaseRevision(), second.getBaseRevision());

        editorOf(first).getActiveScene().findEntityByName("Rock").setName("Boulder");
        assertTrue(first.save());
        editorOf(second).getActiveScene().findEntityByName("Tower").setName("Keep");
        assertFalse(second.save(), "an outdated base is refused");

        assertTrue(errors.get(errors.size() - 1).startsWith("Save conflict"), errors.toString());
        String onDisk = Files.readString(root.resolve("scenes/valley.json"));
        assertTrue(onDisk.contains("Boulder") && !onDisk.contains("Keep"), "the first save stands; nothing was overwritten");

        assertTrue(second.saveMineAsCopy("valley-keep"), errors.toString());
        Path copy = temp.resolve("valley-keep");
        assertTrue(Files.readString(copy.resolve("scenes/valley.json")).contains("Keep"));
        assertNotEquals(manifest(root).projectId(), manifest(copy).projectId());

        assertTrue(first.reloadTheirs());
        assertEquals(manifest(root).revision().id(), first.getBaseRevision());
    }

    @Test
    void shouldOpenReadOnlyAndNeverSaveWhenARequiredCapabilityIsMissing() throws IOException {
        Path root = project("readonly");
        Path manifestFile = root.resolve(ProjectManifest.FILE_NAME);
        Files.writeString(manifestFile, Files.readString(manifestFile).replace("\"ownership/1\"", "\"ownership/1\", \"teleporters/3\""));
        String before = Files.readString(root.resolve("scenes/valley.json"));

        SceneFileController controller = opened(root);

        assertTrue(controller.getProjectAccess().readOnly());
        assertTrue(editorOf(controller).getStatusMessage().contains("teleporters/3"), "the reason is shown");
        editorOf(controller).getActiveScene().findEntityByName("Rock").setName("Changed");
        assertFalse(controller.save());
        assertEquals(before, Files.readString(root.resolve("scenes/valley.json")), "nothing was written");
        assertTrue(errors.get(errors.size() - 1).contains("read-only"), errors.toString());
    }

    @Test
    void shouldKeepAComponentNoClientHereKnowsInTheOpenScene() {
        Path root;
        try {
            root = project("unknown");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        SceneFileController controller = opened(root);
        Entity hero = editorOf(controller).getActiveScene().findEntityByName("Hero");
        // This bare editor registers fewer serializers than the desktop editor, so its unknown components include the mesh: all kept
        assertTrue(hero.getComponents(PreservedComponent.class).stream().map(PreservedComponent::getTypeId).toList()
            .contains("ExampleFutureComponent"));
    }
}
