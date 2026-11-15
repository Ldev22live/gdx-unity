package io.github.ldev22live.gdxunity.editor.core.asset;

import io.github.ldev22live.gdxunity.editor.core.EditorState;
import io.github.ldev22live.gdxunity.editor.core.EditorTestHelper;
import io.github.ldev22live.gdxunity.editor.core.action.DefaultEditorActions;
import io.github.ldev22live.gdxunity.editor.core.file.EditorDialogService;
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
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** File > Import Asset (roadmap M1.5): bringing a file from outside into the project. */
class AssetImportTest {
    @TempDir
    Path temp;

    @BeforeAll
    static void init() {
        EditorTestHelper.initNatives();
    }

    private Path project() throws IOException {
        Path project = temp.resolve("project");
        Files.createDirectories(project);
        return project;
    }

    private Path outside(String name, String text) throws IOException {
        Path file = temp.resolve("downloads").resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    private static List<String> listing(Path root) throws IOException {
        List<String> files = new ArrayList<>();
        if (Files.exists(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).forEach(p -> files.add(root.relativize(p).toString().replace('\\', '/')));
            }
        }
        java.util.Collections.sort(files);
        return files;
    }

    @Test
    void shouldCopyEachKindIntoItsFolderUnderANameNothingElseHasAndLeaveTheOriginal() throws IOException {
        Path project = project();
        Path glb = outside("Rock.glb", "glTF-binary");
        Files.createDirectories(project.resolve("models"));
        Files.writeString(project.resolve("models/Rock.glb"), "mine");

        AssetImport.Result first = AssetImport.importFile(project.toFile(), glb.toFile());
        AssetImport.Result image = AssetImport.importFile(project.toFile(), outside("wall.png", "png").toFile());
        AssetImport.Result sound = AssetImport.importFile(project.toFile(), outside("door.wav", "wav").toFile());
        AssetImport.Result song = AssetImport.importFile(project.toFile(), outside("theme.song", "{}").toFile());

        assertEquals("models/Rock 2.glb", first.asset(), "an existing file is never overwritten");
        assertEquals("mine", Files.readString(project.resolve("models/Rock.glb")));
        assertEquals("textures/wall.png", image.asset());
        assertEquals("sounds/door.wav", sound.asset());
        assertEquals("music/theme.song", song.asset());
        assertEquals(List.of("models/Rock 2.glb"), first.copied());
        assertFalse(first.alreadyInside());
        assertEquals("glTF-binary", Files.readString(glb), "the original is not touched");
    }

    @Test
    void shouldBringAGltfsSeparateBufferAndImageIntoAFolderOfItsOwn() throws IOException {
        Path project = project();
        Path gltf = outside("tree.gltf", "{\"buffers\":[{\"uri\":\"tree.bin\"}],\"images\":[{\"uri\":\"maps/bark%20color.png\"},"
            + "{\"uri\":\"data:image/png;base64,AAAA\"}]}");
        outside("tree.bin", "bin");
        outside("maps/bark color.png", "png");

        AssetImport.Result result = AssetImport.importFile(project.toFile(), gltf.toFile());

        assertEquals("models/tree/tree.gltf", result.asset());
        assertEquals(List.of("models/tree/maps/bark color.png", "models/tree/tree.bin", "models/tree/tree.gltf"), listing(project));
        AssetImport.Result again = AssetImport.importFile(project.toFile(), gltf.toFile());
        assertEquals("models/tree 2/tree.gltf", again.asset(), "a second import gets its own folder");
    }

    @Test
    void shouldStopWithTheNameOfAMissingGltfFileAndLeaveNothingBehind() throws IOException {
        Path project = project();
        Path gltf = outside("broken.gltf", "{\"buffers\":[{\"uri\":\"gone.bin\"}]}");

        IOException failure = assertThrows(IOException.class, () -> AssetImport.importFile(project.toFile(), gltf.toFile()));

        assertTrue(failure.getMessage().contains("gone.bin"), failure.getMessage());
        assertEquals(List.of(), listing(project));
        Path escaping = outside("escape.gltf", "{\"buffers\":[{\"uri\":\"../secret.bin\"}]}");
        outside("../secret.bin", "x");
        assertThrows(IOException.class, () -> AssetImport.importFile(project.toFile(), escaping.toFile()), "a URI may not leave its folder");
    }

    @Test
    void shouldRefuseUnknownAndMissingFilesAndCopyNothingForAFileAlreadyInTheProject() throws IOException {
        Path project = project();
        assertThrows(IOException.class, () -> AssetImport.importFile(project.toFile(), outside("notes.txt", "x").toFile()));
        assertThrows(IOException.class, () -> AssetImport.importFile(project.toFile(), temp.resolve("nope.png").toFile()));
        Files.createDirectories(project.resolve("textures"));
        Files.writeString(project.resolve("textures/in.png"), "png");

        AssetImport.Result inside = AssetImport.importFile(project.toFile(), project.resolve("textures/in.png").toFile());

        assertTrue(inside.alreadyInside());
        assertEquals("textures/in.png", inside.asset());
        assertEquals(List.of(), inside.copied());
        assertEquals(List.of("textures/in.png"), listing(project));
    }

    @Test
    void shouldReportTheFilesAnImportedMaterialNamesThatThisProjectDoesNotHave() throws IOException {
        Path project = project();
        Files.createDirectories(project.resolve("textures"));
        Files.writeString(project.resolve("textures/have.png"), "png");
        Path material = outside("Brick.material", "{\"baseColorTexture\":\"textures/brick.png\",\"normal\":\"textures/have.png\","
            + "\"name\":\"Brick wall\"}");

        AssetImport.Result result = AssetImport.importFile(project.toFile(), material.toFile());

        assertEquals("materials/Brick.material", result.asset());
        assertEquals(List.of("textures/brick.png"), result.unresolvedNames(), "reported, not guessed at");
        assertFalse(Files.exists(project.resolve("textures/brick.png")));
    }

    @Test
    void shouldImportThroughTheActionAndSayHowTheFileIsUsed() throws IOException {
        Path project = project();
        Path glb = outside("Crate.glb", "glTF-binary");
        EditorState state = new EditorState();
        List<String> imported = new ArrayList<>();
        StubDialogs dialogs = new StubDialogs();

        dialogs.next = glb.toFile();
        assertTrue(DefaultEditorActions.importAsset(state, dialogs, project.toFile(), imported::add));
        assertEquals(List.of("models/Crate.glb"), imported);
        assertFalse(state.isStatusError());
        assertTrue(state.getStatusMessage().contains("Imported Crate.glb as models/Crate.glb")
            && state.getStatusMessage().contains("into the scene view"), state.getStatusMessage());
        assertTrue(dialogs.extensions.containsAll(List.of("glb", "gltf", "png", "wav", "song", "prefab", "material", "terrain")));

        dialogs.next = null;
        assertFalse(DefaultEditorActions.importAsset(state, dialogs, project.toFile(), imported::add), "cancelled");
        dialogs.next = outside("notes.txt", "x").toFile();
        assertFalse(DefaultEditorActions.importAsset(state, dialogs, project.toFile(), imported::add));
        assertTrue(state.isStatusError());
        assertEquals(1, dialogs.errors.size());
        assertEquals(1, imported.size());
    }

    private static final class StubDialogs implements EditorDialogService {
        File next;
        List<String> extensions = List.of();
        final List<String> errors = new ArrayList<>();

        @Override
        public Optional<File> chooseOpenFile(String title, File dir, String desc, String ext) {
            return Optional.ofNullable(next);
        }

        @Override
        public Optional<File> chooseOpenFile(String title, File dir, String desc, List<String> exts) {
            extensions = exts;
            return Optional.ofNullable(next);
        }

        @Override
        public Optional<File> chooseSaveFile(String title, File file, String desc, String ext) {
            return Optional.empty();
        }

        @Override
        public UnsavedChangesChoice askUnsavedChanges(String documentName) {
            return UnsavedChangesChoice.CANCEL;
        }

        @Override
        public void showError(String title, String message) {
            errors.add(message);
        }
    }
}
